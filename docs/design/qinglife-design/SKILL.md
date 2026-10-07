---
name: qinglife-design
description: >
  Design, review, and refine user-facing UI for the Qinglife (轻生活) native WeChat Mini Program.
  Use when creating a new miniapp page or component, redesigning an existing page,
  reviewing WXML/WXSS, changing visual hierarchy, typography, surfaces, states,
  navigation, forms, or interaction feedback in apps/miniapp.
  Preserve Qinglife's quiet, natural, restrained lifestyle identity while reducing
  AI-generated UI patterns and keeping user tasks clear and efficient.
---

# Qinglife Design

Use this skill for user-facing design work inside `apps/miniapp`.

Qinglife is a native WeChat Mini Program.  
Design for WXML / WXSS / native Mini Program interaction patterns.

Do not assume DOM, React, Tailwind, GSAP, browser-only APIs, or desktop web interaction patterns.

## 1. Design north star

Qinglife should feel:

**安静 · 自然 · 克制 · 温暖 · 有生活方式品牌感 · 有少量编辑感与东方气质**

The product should feel considered, not decorated.

Prefer:

**content over decoration  
hierarchy over components  
spacing over containers  
clarity over atmosphere  
real content over visual metaphors  
restraint over novelty**

Do not turn Qinglife into:

- a SaaS dashboard
- a generic wellness app
- an Apple imitation
- a Japanese lifestyle template
- a magazine that sacrifices usability
- a visually polished AI-generated component showcase

## 2. Priority of decisions

When making a UI decision, use this order:

1. User's current task
2. Information hierarchy
3. Readability and interaction clarity
4. Qinglife brand character
5. Visual refinement
6. Decoration and motion

A lower-priority goal must not damage a higher-priority goal.

If a returning user already has an active task, registration, itinerary, record, or status, surface that before promotional or atmospheric content.

Brand expression should live inside the experience, not stand between the user and the task.

## 3. Relationship with other design skills

For Qinglife, this skill defines the project-specific design direction.

External design skills may provide general methods, but must not override Qinglife's product identity.

When used together:

- `frontend-design`: use for general composition, hierarchy and design quality.
- `taste-skill`: use to detect generic AI UI, over-design and visual sameness.
- `qiaomu-design`: preferably use as a critique/review layer after a design direction exists.

Do not combine every suggestion from every skill.

When recommendations conflict, prefer the solution that best satisfies this document and the real user task.

## 4. Start with structure, not styling

Before changing visual details, identify:

- Who is using this page?
- What are they trying to do now?
- What is the single most important information or action?
- What information supports that task?
- What can be visually quieter?
- What can be removed?

Do not solve information architecture problems with stronger colors, larger cards or more decoration.

For existing pages, preserve business logic and product behavior unless the task explicitly asks to change them.

Judge cross-page consistency primarily by typography roles, information priority, action semantics and state expression. Different tasks may use different layouts and information densities.

## 5. One primary visual focus

Each screen or major viewport should normally have one dominant focus.

Do not give every section equal visual weight.

Avoid sequences where every block repeats:

`Pill → large title → explanatory copy → Card → button`

Variation should come from content structure, not decorative novelty.

When several modules compete for attention, reduce the visual weight of secondary modules before increasing the primary one.

## 6. Surface and Card rules

**Default: no Card.**

Use a Card or independent surface only when the content genuinely needs to be perceived as one independent object.

Good candidates:

- one activity
- one registration result
- one historical post
- one clearly bounded record
- one confirmation group that must be distinguished from surrounding content

Usually not Card-worthy:

- one form field
- one paragraph of explanation
- one status line
- one metadata group
- one section heading
- one short instruction
- every item in a continuous list

Prefer:

- whitespace
- alignment
- typography
- subtle separators
- grouping by proximity

before adding another surface.

Avoid nested combinations such as:

`page surface → Card → field Card → input surface`

unless each boundary carries a real semantic role.

Different information roles should not automatically share the same large radius, shadow and background treatment.

## 7. Typography roles

Typography must communicate function.

### Serif

Use Qinglife's serif style selectively for:

- brand masthead
- editorial/article titles
- meaningful emotional statements
- rare expressive moments

Serif is a signature, not a default UI font.

### System sans-serif

Use system sans-serif for:

- buttons
- navigation
- status
- dates
- locations
- prices and pricing conditions
- names
- form labels
- inputs
- metadata
- instructions
- operational information

Do not make functional information poetic through typography.

Do not enlarge an entire price sentence merely to emphasize the numeric price.

Prefer fewer meaningful text roles over many slightly different font sizes.

## 8. Color and readability

Preserve Qinglife's warm paper, clay, sage and natural palette.

Do not replace the brand with cold gray, neon, blue-purple technology colors or high-contrast SaaS styling.

However:

**soft does not mean faint.**

Dates, locations, fees, navigation labels, section labels and important supporting information must remain easy to read.

Do not reduce important information to low-contrast decorative text simply to create a gentle mood.

Use muted colors for genuinely secondary information, not for information the user must verify.

Secondary dates, deadlines, fees and states must remain legible in real usage conditions, including narrow screens. Reduce their emphasis through hierarchy and spacing, not simply smaller type or lower contrast.

## 9. Pill / Badge rules

Pills and badges are reserved primarily for:

- status
- classification
- filters
- compact semantic attributes

Do not use Pills simply to make text look designed.

Avoid Pills for:

- greetings
- emotional sentences
- ordinary explanations
- decorative introductions
- text that merely repeats the next heading

If removing the Pill does not reduce comprehension, prefer plain text.

Different business states should remain distinguishable.  
Do not merge unrelated states into one decorative badge merely to reduce component count.

## 10. Spacing

Whitespace should clarify relationships.

Use spacing to answer:

- what belongs together?
- what starts a new section?
- what deserves a pause?

Do not create "air" by applying large margins uniformly everywhere.

Group spacing should be visibly tighter than section spacing.

Long reading pages may breathe more than:

- forms
- account pages
- task flows
- record lists

Do not make functional pages unnecessarily long in order to feel calm.

## 11. Decoration

Decoration must earn its place.

Qinglife may use subtle editorial or tactile details, but avoid stacking multiple metaphors.

Examples of expressive elements include:

- paper texture
- soft light
- large radius
- soft shadow
- tape
- seal
- serif display typography
- atmospheric gradients
- breathing backgrounds
- decorative objects

Do not make several of these compete on the same screen.

**As a default, allow only one clearly expressive visual language to dominate a viewport.**

If typography already provides strong editorial character, reduce decorative surfaces.

If authentic photography already provides atmosphere, reduce gradients and visual props.

Never add decoration merely because the page feels "too simple."

Simple is acceptable.

## 12. Images and content assets

Prefer authentic, content-related assets.

Do not use one generic lifestyle scene to represent unrelated content.

For articles, activities and experiences:

- real relevant photography is valuable
- content-specific imagery is valuable
- no image is better than an unrelated decorative image

Do not automatically generate a substitute illustration when an authentic image is unavailable.

Let typography and content carry the page when necessary.

## 13. Motion

Motion must explain:

- feedback
- transition
- progress
- state change
- navigation

Do not use motion merely to make the interface feel premium.

Avoid automatic motion that interrupts reading or form completion.

Authentic content alone does not justify autoplay, drifting text, carousels or continuous animation. Default to static presentation unless these treatments provide a clear benefit for the task or content comprehension.

Be especially cautious with:

- autoplay carousels
- continuously breathing backgrounds
- looping decorative animation
- movement unrelated to state

Preserve useful press feedback, loading feedback, progress transitions and meaningful state animation.

A quiet product should also be quiet in time.

## 14. Buttons and actions

Maintain clear action hierarchy.

Prefer:

1. one primary action
2. optional secondary action
3. quiet text action when appropriate

Do not repeat multiple visually strong CTAs for the same task.

Do not style status text as a clickable button when clicking cannot lead to meaningful progress.

A disabled, unavailable or informational state should look different from an actionable control.

Do not use visual novelty to make every action important.

## 15. Forms

Forms are tasks, not editorial compositions.

Optimize for:

- scanning
- completion
- error recovery
- understanding selection rules

Avoid wrapping every field in an independent Card.

Use clear grouping and section headings.

For single-select and multi-select questions, make selection rules understandable before trial and error.

If an option such as “无” is mutually exclusive, make that behavior understandable.

Validation should identify the relevant field or group whenever feasible.

Do not rely only on a generic Toast when the user must search a long page to find the error.

When changing multi-step forms, consider scroll position and make the start of the new step discoverable.

Keep necessary privacy explanations, but do not visually amplify every privacy sentence into another independent surface.

## 16. State and feedback

Design explicit states for:

- loading
- empty
- error
- success
- disabled
- unavailable
- stale/refreshing when relevant

Do not visually collapse loading, empty and error into the same generic soft card.

Error messages should describe what is known.

Do not invent emotional explanations for technical failure.

Success feedback must not promise capabilities the product does not actually provide.

Examples:

- Do not imply a notification will definitely arrive unless that delivery exists.
- Do not say reminders are enabled when only a local preference was saved.
- Do not confirm a saved result if users cannot later retrieve it through the expected flow.

Visual trust depends on product truth.

## 17. Navigation

Preserve native WeChat conventions when they work.

Do not rebuild native interaction solely for aesthetic consistency.

Shared navigation must consider:

- safe areas
- capsule position
- long-page behavior
- persistent access to critical navigation
- readable selected and unselected states

On long content or activity-detail pages, verify that the user retains a reasonable way to leave or go back after scrolling.

Do not add another navigation layer unless the information architecture requires it.

## 18. Lists and repeated content

Repeated content should normally become quieter as quantity increases.

For content browsing, consider:

- separators
- typography
- aligned metadata
- compact rows

before Card grids.

Qinglife's simple reading-list and directory-row patterns are good references.

Do not make saved articles, records or services visually heavier simply because they are displayed on a different page.

Reuse semantic patterns, not just CSS classes.

## 19. New page decision process

When creating a new page:

### Step 1 — Define the task
Write one sentence:

`The user comes here to ______.`

### Step 2 — Define hierarchy
Identify:

- primary information/action
- secondary information
- optional information

### Step 3 — Choose the lightest structure that works
Start with:

- page
- heading
- content
- separator
- action

Add Card, Pill, imagery, shadow or motion only when a clear reason exists.

### Step 4 — Apply Qinglife identity
Use existing palette, typography and shared components where appropriate.

Do not invent a new aesthetic language for one page.

### Step 5 — Remove
Before implementation is considered finished, ask:

- Can one Card disappear?
- Can one Pill become plain text?
- Can one decoration disappear?
- Can one visual level disappear?
- Can the user reach the main task sooner?

If yes, prefer the simpler version unless information clarity decreases.

## 20. Existing page redesign process

For an existing page:

1. Read its WXML, WXSS and related shared styles/components.
2. Understand its current business behavior before editing.
3. Identify what is already working and preserve it.
4. Separate structural problems from visual styling problems.
5. Fix hierarchy before appearance.
6. Remove unnecessary visual treatment before adding new treatment.
7. Reuse existing Qinglife patterns when they already solve the problem.
8. Avoid unrelated refactors.
9. Keep changes scoped to the requested design objective.
10. After implementation, review the page again for AI-generated visual habits.

## 21. Preserve these Qinglife assets

Do not casually remove:

- warm paper / natural base palette
- restrained clay and sage accents
- system sans-serif body text
- selective serif editorial contrast
- real activity covers
- explicit dates, places, fees and states
- stable four-channel bottom navigation
- line-based content lists
- directory-style service rows
- meaningful real-world step numbers and chronological order
- native safe-area handling
- private, voluntary, non-gamified tone
- acceptance of rest and incomplete participation
- draft saving and useful progress states

These are product identity, not merely decoration.

## 22. Common failure patterns

Actively check for:

- every section becoming a Card
- excessive large rounded rectangles
- repeated Pill → title → copy → button compositions
- gradients added without semantic purpose
- shadows on every surface
- decorative objects unrelated to content
- serif spreading into functional data
- faint text used to manufacture elegance
- equal visual weight across all modules
- excessive explanatory copy before the main task
- generic wellness imagery
- over-designed empty states
- unnecessary automatic animation
- adding icons simply because a row feels empty
- turning a task page into a landing page
- redesigning business logic during a visual cleanup
- introducing web patterns that do not map naturally to native WeChat Mini Programs

## 23. Final review

Before declaring a Qinglife UI task complete, verify:

Review all actually used branches, including history, detail, empty and error states, together with inherited shared styles. Do not judge a page only from its default first viewport.

- Is the user's main task obvious?
- Does the most important content appear early enough?
- Is every Card necessary?
- Is every Pill semantic?
- Is serif used selectively?
- Are important facts readable?
- Is decorative treatment restrained?
- Does motion have a purpose?
- Are state and error behaviors understandable?
- Does the page still feel like Qinglife?
- Does it feel less designed by AI, not merely more designed?

When uncertain, choose the quieter solution.

Once the main task is clear, page roles are stable, core states are complete and obvious AI UI patterns have been removed, stop aesthetic iteration. Move to real-device acceptance or real user feedback instead of continuing redesign solely for greater refinement.