package com.yicai.web.controller.life;
import com.yicai.common.core.domain.AjaxResult;
import com.yicai.common.utils.SecurityUtils;
import com.yicai.life.service.ArticleContentService;
import com.yicai.life.domain.bo.ArticlePlacementBo;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
@RestController @RequiredArgsConstructor
public class ArticleContentController {
    private final ArticleContentService content;
    @GetMapping("/life/essay/placements") @PreAuthorize("@ss.hasPermi('life:essay:query')")
    public AjaxResult<?> list(){return AjaxResult.success(content.list());}
    @PutMapping("/life/essay/placements/{key}") @PreAuthorize("@ss.hasPermi('life:essay:edit') && @ss.hasPermi('life:essay:query')")
    public AjaxResult<?> save(@PathVariable String key,@RequestBody ArticlePlacementBo bo){return AjaxResult.success(content.save(key,bo,SecurityUtils.getLoginUser().getUser().getUserId()));}
    @GetMapping("/life/essay/placements/{key}/audit") @PreAuthorize("@ss.hasPermi('life:essay:query')")
    public AjaxResult<?> audit(@PathVariable String key){return AjaxResult.success(content.audit(key));}
    @GetMapping("/life/essay/{id}/provenance") @PreAuthorize("@ss.hasPermi('life:essay:query')")
    public AjaxResult<?> provenance(@PathVariable Long id){return AjaxResult.success(content.provenance(id));}
    @GetMapping("/life/essay/{id}/content-review") @PreAuthorize("@ss.hasPermi('life:essay:query')")
    public AjaxResult<?> review(@PathVariable Long id){return AjaxResult.success(content.review(id));}
    @GetMapping("/app/qinglife/content-placements/{key}")
    public AjaxResult<?> publicLink(@PathVariable String key){return AjaxResult.success(content.publicLink(key));}
}
