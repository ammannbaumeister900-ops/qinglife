package com.yicai.web.tools;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.core.io.support.EncodedResource;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.*;
import java.util.*;

/** Explicit offline migration tool. Never called by application startup. DDL failures require restore. */
public final class DatabaseMigration {
    private DatabaseMigration() {}
    public static void main(String[] args) throws Exception {
        if(args.length!=1)throw new IllegalArgumentException("Pass the repository server directory");
        String url=System.getenv("QINGLIFE_MIGRATION_URL");
        if(url==null)throw new IllegalArgumentException("QINGLIFE_MIGRATION_URL is required");
        try(Connection c=DriverManager.getConnection(url,System.getenv("QINGLIFE_MIGRATION_USER"),System.getenv("QINGLIFE_MIGRATION_PASSWORD"))) {
            run(c,Paths.get(args[0]));
        }
    }
    public static List<Path> files(Path server) throws Exception {
        List<Path> files=new ArrayList<>();files.add(server.resolve("bootstrap/legacy-schema.sql"));
        try(java.util.stream.Stream<Path> paths=Files.list(server.resolve("migrations"))) {
            paths.filter(p->p.getFileName().toString().matches("V[0-9_]+__.*\\.sql")).forEach(files::add);
        }
        files.add(server.resolve("api/box-system/src/main/resources/db/migration/V1_3_10__disable_reschedule.sql"));
        files.subList(1,files.size()).sort((a,b)->{
            String[] x=a.getFileName().toString().split("__")[0].substring(1).split("_");
            String[] y=b.getFileName().toString().split("__")[0].substring(1).split("_");
            for(int i=0;i<Math.min(x.length,y.length);i++){int d=Integer.compare(Integer.parseInt(x[i]),Integer.parseInt(y[i]));if(d!=0)return d;}return Integer.compare(x.length,y.length);
        });return files;
    }
    public static String checksum(Path p) throws Exception {
        // Canonical line endings keep a Windows checkout equivalent to Linux.
        byte[] bytes=new String(Files.readAllBytes(p),StandardCharsets.UTF_8).replace("\r\n","\n").getBytes(StandardCharsets.UTF_8);
        StringBuilder s=new StringBuilder();for(byte b:MessageDigest.getInstance("SHA-256").digest(bytes))s.append(String.format("%02x",b&255));return s.toString();
    }
    public static void run(Connection c,Path server) throws Exception {
        if(!c.getAutoCommit())throw new IllegalArgumentException("Migration requires its own autocommit connection");
        String lock="ql-migrate-"+c.getCatalog();
        try(PreparedStatement p=c.prepareStatement("SELECT GET_LOCK(?,0)")){p.setString(1,lock);try(ResultSet r=p.executeQuery()){r.next();if(r.getInt(1)!=1)throw new IllegalStateException("Another migration holds the database lock");}}
        try {
            boolean journal;
            try(ResultSet r=c.getMetaData().getTables(c.getCatalog(),null,"ql_migration_run",new String[]{"TABLE"})){journal=r.next();}
            if(!journal) {
                try(Statement s=c.createStatement();ResultSet r=s.executeQuery("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE()")) {
                    r.next();if(r.getInt(1)!=0)throw new IllegalStateException("Existing untracked database: rehearse and establish a verified baseline; automatic adoption is forbidden");
                }
                try(Statement s=c.createStatement()){s.execute("CREATE TABLE ql_migration_run(name VARCHAR(200) PRIMARY KEY,sha256 CHAR(64) NOT NULL,state VARCHAR(16) NOT NULL,completed_at DATETIME(3)) ENGINE=InnoDB");}
            }
            for(Path file:files(server)) {
                String name=file.getFileName().toString(),hash=checksum(file);
                try(PreparedStatement p=c.prepareStatement("SELECT sha256,state FROM ql_migration_run WHERE name=?")) {
                    p.setString(1,name);try(ResultSet r=p.executeQuery()){if(r.next()){
                        if(!hash.equals(r.getString(1))||!"complete".equals(r.getString(2)))throw new IllegalStateException("Migration checksum mismatch or interrupted DDL: "+name+". Restore the rehearsal backup before retrying.");
                        continue;
                    }}
                }
                try(PreparedStatement p=c.prepareStatement("INSERT INTO ql_migration_run(name,sha256,state) VALUES(?,?,'running')")){p.setString(1,name);p.setString(2,hash);p.executeUpdate();}
                ScriptUtils.executeSqlScript(c,new EncodedResource(new ByteArrayResource(Files.readAllBytes(file)),StandardCharsets.UTF_8));
                try(PreparedStatement p=c.prepareStatement("UPDATE ql_migration_run SET state='complete',completed_at=NOW(3) WHERE name=?")){p.setString(1,name);p.executeUpdate();}
                System.out.println("Applied "+name);
            }
        } finally {
            try(PreparedStatement p=c.prepareStatement("SELECT RELEASE_LOCK(?)")){p.setString(1,lock);p.execute();}
        }
    }
}
