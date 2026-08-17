package com.galenlinter.documentation

/**
 * Reference text shown on hover and in Quick Documentation.
 *
 * Sourced from `docs/galen-spec-reference.md`, which is itself tagged by provenance — several
 * entries below record behaviour confirmed from Galen's source that its own documentation gets
 * wrong or omits (`near` requiring a side, `aligned` requiring an edge, `@lib` existing at all).
 */
data class GalenDoc(
    val title: String,
    val summary: String,
    val syntax: String? = null,
    val values: String? = null,
    val example: String? = null,
    val note: String? = null,
)

object GalenDocs {

    fun spec(name: String): GalenDoc? = SPECS[name]

    fun statement(name: String): GalenDoc? = STATEMENTS[name]

    fun keyword(word: String): GalenDoc? = KEYWORDS[word]

    /** Shared explanation of the range grammar, referenced by most size and position specs. */
    private const val RANGE_VALUES =
        "A range: <code>100px</code>, <code>50 to 200px</code>, <code>&gt; 40px</code>, " +
            "<code>&lt; 40px</code>, <code>&gt;= 40px</code>, <code>&lt;= 40px</code>, " +
            "<code>~ 100px</code>. Decimals are allowed. Relative form: " +
            "<code>95 to 100 % of main/width</code>, where the property is " +
            "<code>width</code> or <code>height</code>."

    private const val SIDES_VALUES =
        "One or more of <code>left</code>, <code>right</code>, <code>top</code>, " +
            "<code>bottom</code>. Several range+side groups may be comma-separated."

    private val SPECS: Map<String, GalenDoc> = mapOf(
        "width" to GalenDoc(
            title = "width &lt;range&gt;",
            summary = "Checks the rendered width of the object.",
            syntax = "width &lt;range&gt;",
            values = RANGE_VALUES,
            example = "button:\n    width 100 px\n    width &lt; 101 px\n    width 95 to 100 % of main/width",
        ),
        "height" to GalenDoc(
            title = "height &lt;range&gt;",
            summary = "Checks the rendered height of the object.",
            syntax = "height &lt;range&gt;",
            values = RANGE_VALUES,
            example = "button:\n    height 25px\n    height 90 to 100 % of main/height",
        ),
        "near" to GalenDoc(
            title = "near &lt;object&gt; &lt;range&gt; &lt;sides&gt;",
            summary = "Checks that the object sits the given distance from another object.",
            syntax = "near &lt;object&gt; &lt;range&gt; &lt;side&gt;... [, &lt;range&gt; &lt;side&gt;...]",
            values = SIDES_VALUES,
            example = "textfield:\n    near button 10px left\n    near button 5px top, 10px left",
            note = "At least one side is <b>required</b>. Galen rejects a bare distance, " +
                "even though its own documentation contains an example without one.",
        ),
        "above" to GalenDoc(
            title = "above &lt;object&gt; &lt;range&gt;",
            summary = "Checks that the object is the given distance above another object.",
            syntax = "above &lt;object&gt; &lt;range&gt;",
            values = RANGE_VALUES,
            example = "caption:\n    above description 10 to 20 px",
        ),
        "below" to GalenDoc(
            title = "below &lt;object&gt; &lt;range&gt;",
            summary = "Checks that the object is the given distance below another object.",
            syntax = "below &lt;object&gt; &lt;range&gt;",
            values = RANGE_VALUES,
            example = "description:\n    below caption 10 to 20 px",
        ),
        "left-of" to GalenDoc(
            title = "left-of &lt;object&gt; &lt;range&gt;",
            summary = "Checks that the object is the given distance to the left of another object.",
            syntax = "left-of &lt;object&gt; &lt;range&gt;",
            values = RANGE_VALUES,
            example = "textfield:\n    left-of button 10px",
        ),
        "right-of" to GalenDoc(
            title = "right-of &lt;object&gt; &lt;range&gt;",
            summary = "Checks that the object is the given distance to the right of another object.",
            syntax = "right-of &lt;object&gt; &lt;range&gt;",
            values = RANGE_VALUES,
            example = "button:\n    right-of textfield 10px",
        ),
        "inside" to GalenDoc(
            title = "inside [partly] &lt;object&gt; [&lt;range&gt; &lt;sides&gt;]",
            summary = "Checks that the object is visually inside another object, optionally at " +
                "given distances from its edges.",
            syntax = "inside [partly] &lt;object&gt; [&lt;range&gt; &lt;side&gt;...]",
            values = "$SIDES_VALUES <code>partly</code> checks only the listed ranges and " +
                "tolerates the object sticking out.",
            example = "button:\n    inside container 10px left right, 20px top bottom\n" +
                "    inside partly container 10px top left",
            note = "Sides are optional here, unlike <code>near</code>, but omitting them only " +
                "checks containment — specifying the edges is the useful form.",
        ),
        "on" to GalenDoc(
            title = "on [corner] edge &lt;object&gt; &lt;range&gt; &lt;sides&gt;",
            summary = "Checks the object's offset from a named corner of another object. Unlike " +
                "<code>inside</code>, it tolerates the object lying on top of the other.",
            syntax = "on [top|bottom] [left|right] edge &lt;object&gt; &lt;range&gt; &lt;side&gt;...",
            values = "The corner takes at most one of <code>top</code>/<code>bottom</code> and one " +
                "of <code>left</code>/<code>right</code>, and defaults to top-left.",
            example = "user-picture-label:\n    on top left edge user-picture 20px left, 10px bottom",
            note = "The word <code>edge</code> is <b>required</b>; Galen throws " +
                "<code>Missing \"edge\"</code> without it.",
        ),
        "aligned" to GalenDoc(
            title = "aligned &lt;direction&gt; &lt;edge&gt; &lt;object&gt; [error rate]",
            summary = "Checks that the object lines up with another object.",
            syntax = "aligned horizontally|vertically &lt;edge&gt; &lt;object&gt; [&lt;n&gt;px]",
            values = "<code>horizontally</code> accepts <code>all</code>, <code>top</code>, " +
                "<code>bottom</code>, <code>centered</code>. <code>vertically</code> accepts " +
                "<code>all</code>, <code>left</code>, <code>right</code>, <code>centered</code>. " +
                "A trailing pixel value is a tolerance.",
            example = "menu_item-1:\n    aligned horizontally all menu_item-2 1px",
            note = "Both the direction and the edge are <b>required</b>, and the pairs above are " +
                "the only legal ones.",
        ),
        "centered" to GalenDoc(
            title = "centered &lt;direction&gt; inside|on &lt;object&gt; [error rate]",
            summary = "Checks that the object is centred within, or on top of, another object.",
            syntax = "centered horizontally|vertically|all inside|on &lt;object&gt; [&lt;n&gt;px]",
            values = "<code>inside</code> also requires containment; <code>on</code> tolerates " +
                "the object sticking out. A trailing pixel value is a tolerance.",
            example = "button:\n    centered horizontally inside box 10px\n    centered all inside box",
        ),
        "text" to GalenDoc(
            title = "text [operations] &lt;matcher&gt; \"expected\"",
            summary = "Checks the object's visible text. This is the text as a browser renders it, " +
                "so whitespace is collapsed — not the raw HTML.",
            syntax = "text [lowercase|uppercase|singleline]... is|contains|starts|ends|matches \"...\"",
            values = "<code>matches</code> takes a <b>Java</b> regular expression. Operations may " +
                "be chained.",
            example = "greeting:\n    text is \"Welcome!\"\n    text lowercase contains \"welcome\"\n" +
                "    text matches \"Welcome .* today\"",
            note = "Galen accepts <i>any</i> unrecognised word as an operation, so a typo such as " +
                "<code>lowercse</code> runs and silently does nothing.",
        ),
        "ocr" to GalenDoc(
            title = "ocr text [operations] &lt;matcher&gt; \"expected\"",
            summary = "Checks text recognised from a screenshot of the object, via the Google " +
                "Vision API. Useful where text is rendered into an image.",
            syntax = "ocr text [operations]... is|contains|starts|ends|matches \"...\"",
            values = "Same matchers as <code>text</code>.",
            example = "header.caption:\n    ocr text is \"My Awesome Website!\"",
            note = "Since Galen 2.4. Requires <code>galen.ocr.google.vision.key</code> to be set.",
        ),
        "css" to GalenDoc(
            title = "css &lt;property&gt; &lt;matcher&gt; \"expected\"",
            summary = "Checks the computed value of a CSS property.",
            syntax = "css &lt;property&gt; is|contains|starts|ends|matches \"...\"",
            values = "<code>lowercase</code> and <code>uppercase</code> are <b>not</b> supported here.",
            example = "login-button:\n    css font-size is \"18px\"\n    css font-family contains \"Arial\"",
            note = "Galen's own documentation advises using this sparingly: it couples layout " +
                "tests to implementation detail, which is what Galen exists to avoid.",
        ),
        "absent" to GalenDoc(
            title = "absent",
            summary = "Checks that the object is missing from the page, or present but not visible " +
                "(for example <code>display: none</code>). Takes no arguments.",
            example = "comments:\n    absent",
        ),
        "visible" to GalenDoc(
            title = "visible",
            summary = "Checks that the object is present and visible. Takes no arguments.",
            example = "comments:\n    visible",
            note = "Every other spec already requires visibility, so this is only needed on its own.",
        ),
        "contains" to GalenDoc(
            title = "contains [partly] &lt;objects&gt;",
            summary = "Checks that the object visually contains the listed objects.",
            syntax = "contains [partly] &lt;object&gt;[, &lt;object&gt;]...",
            values = "Object patterns are allowed: <code>*</code> matches any characters, " +
                "<code>#</code> matches digits. <code>partly</code> tolerates partial containment.",
            example = "menu:\n    contains menu_item-*\nbox:\n    contains partly box-item-1, box-item-2",
        ),
        "count" to GalenDoc(
            title = "count &lt;filter&gt; &lt;pattern&gt; is &lt;range&gt;",
            summary = "Checks how many objects match a pattern. Usually written on the " +
                "<code>global</code> pseudo-object.",
            syntax = "count any|visible|absent &lt;pattern&gt; is &lt;range&gt;",
            values = "The range carries <b>no unit</b>: <code>is 3</code>, <code>is 4 to 5</code>, " +
                "<code>is &lt; 6</code>.",
            example = "global:\n    count any menu_item-* is 4 to 5",
            note = "Since Galen 2.1.",
        ),
        "component" to GalenDoc(
            title = "component [frame] &lt;file.gspec&gt; [, name value]...",
            summary = "Runs another spec file against this object, so a repeated layout can be " +
                "described once. Inside it, <code>parent</code> and <code>self</code> refer to this object.",
            syntax = "component [frame] &lt;file.gspec&gt;[, &lt;name&gt; &lt;value&gt;]...",
            values = "<code>frame</code> enters an iframe. Arguments are readable as " +
                "<code>${'$'}{name}</code> in the component.",
            example = "user-profile-*:\n    component user-profile.gspec, isUserLogged true",
        ),
        "color-scheme" to GalenDoc(
            title = "color-scheme &lt;percentage&gt; &lt;colour&gt;, ...",
            summary = "Checks the distribution of colours across the object's area.",
            syntax = "color-scheme &lt;range&gt;% &lt;colour&gt;[, &lt;range&gt;% &lt;colour&gt;]...",
            values = "Colour names or hex values. Gradients are written " +
                "<code>#000-#555-#955</code>.",
            example = "login-form:\n    color-scheme 10% white, 4 to 5 % black, &lt; 30% #f845b7",
            note = "Gradients since Galen 2.3.",
        ),
        "image" to GalenDoc(
            title = "image file &lt;path&gt;, &lt;options&gt;",
            summary = "Compares the object's rendered pixels against a sample image.",
            syntax = "image file &lt;path&gt;[, error &lt;n&gt;px|%][, tolerance &lt;n&gt;]" +
                "[, stretch][, area l t w h][, analyze-offset &lt;n&gt;][, crop-if-outside]" +
                "[, ignore-objects [...]][, filter|filter-a|filter-b|map-filter &lt;filter&gt;]",
            values = "<code>file</code> may repeat — the closest-matching sample wins — and " +
                "accepts <code>*</code> globs. <code>tolerance</code> is the maximum colour " +
                "difference per pixel, default 30. Filters: <code>blur</code>, " +
                "<code>saturation</code>, <code>contrast</code> (0–258), <code>denoise</code> " +
                "(map only), <code>quantinize</code>, <code>mask</code>, <code>replace-colors</code>.",
            example = "menu_item-1:\n    image file imgs/item.png, error 1%, filter blur 4, " +
                "map-filter denoise 5",
        ),
    )

    private val STATEMENTS: Map<String, GalenDoc> = mapOf(
        "@objects" to GalenDoc(
            title = "@objects",
            summary = "Declares the page objects and how to find them. Entries may nest, producing " +
                "dotted names such as <code>panel.input</code>.",
            syntax = "&lt;name&gt; [@(l, t, w, h)] [@grouped(...)] [id|css|xpath] &lt;locator&gt;",
            values = "The locator type defaults to <code>css</code>. A name containing " +
                "<code>*</code> declares a family: Galen names the matches " +
                "<code>name-1</code>, <code>name-2</code>, and so on.",
            example = "@objects\n    header          #header\n    menu_item-*     css     #menu li a",
        ),
        "@groups" to GalenDoc(
            title = "@groups",
            summary = "Names sets of objects so a spec can address them all at once with " +
                "<code>&amp;groupName</code>.",
            syntax = "&lt;group&gt; &lt;object&gt;, ...   or   (&lt;group&gt;, &lt;group&gt;) &lt;object&gt;, ...",
            example = "@groups\n    skeleton   header, menu, footer",
            note = "Since Galen 2.2.",
        ),
        "@set" to GalenDoc(
            title = "@set",
            summary = "Declares variables substituted into specs via <code>${'$'}{name}</code>. " +
                "The value is raw text, so it may carry its own operator and unit.",
            syntax = "@set &lt;name&gt; &lt;value&gt;   or a block of &lt;name&gt; &lt;value&gt; lines",
            values = "Names must match <code>[a-zA-Z_][a-zA-Z0-9_]*</code> — narrower than object " +
                "names, which also allow <code>-</code> and <code>.</code>.",
            example = "@set\n    gutter    10 to 20px",
        ),
        "@script" to GalenDoc(
            title = "@script",
            summary = "Loads a JavaScript file, or defines JavaScript inline as an indented block. " +
                "Functions defined here are callable from <code>${'$'}{...}</code>.",
            syntax = "@script &lt;file.js&gt;   or   @script followed by an indented block",
            example = "@script i18n.function.js\n\ngreeting:\n    text is \"${'$'}{i18n('hello')}\"",
        ),
        "@import" to GalenDoc(
            title = "@import",
            summary = "Merges another spec file's <b>objects and specs</b> into this one — not " +
                "merely running it. Imported objects become referenceable by name.",
            syntax = "@import &lt;file.gspec&gt;",
            values = "Resolved relative to the importing file's directory. Classpath resources " +
                "also work. Repeated and circular imports are skipped, not errors.",
            example = "@import header.gspec",
        ),
        "@lib" to GalenDoc(
            title = "@lib",
            summary = "Loads a spec library bundled inside the Galen distribution, rather than a " +
                "file on disk.",
            syntax = "@lib &lt;libraryName&gt;",
            values = "Resolved from <code>/spec-libs/&lt;name&gt;/&lt;name&gt;.gspec</code> and " +
                "whitelisted by <code>/spec-libs/libs.list</code>.",
            note = "Undocumented in Galen's own spec language guide, but real.",
        ),
        "@rule" to GalenDoc(
            title = "@rule",
            summary = "Defines a reusable named check, invoked later with <code>|</code>. " +
                "Parameters are captured with <code>%{name}</code>.",
            syntax = "@rule &lt;text with %{params}&gt;   then an indented body",
            values = "A parameter may carry a regex: <code>%{size: [0-9]+}</code>. On an object, " +
                "<code>${'$'}{objectName}</code> is supplied automatically.",
            example = "@rule %{name} should be squared\n    ${'$'}{name}:\n        width 100% of ${'$'}{name}/height",
            note = "Since Galen 1.6.",
        ),
        "@ruleBody" to GalenDoc(
            title = "@ruleBody",
            summary = "Inside a rule, runs the block supplied at the invocation site — letting a " +
                "rule wrap other specs, like a custom conditional.",
            example = "@rule if %{objectName} is visible\n    @if ${'$'}{isVisible(objectName)}\n        @ruleBody",
            note = "Since Galen 2.2.",
        ),
        "@on" to GalenDoc(
            title = "@on",
            summary = "Applies the indented specs only when the test runs with one of the given tags — " +
                "how a single spec describes several device sizes.",
            syntax = "@on &lt;tag&gt;[, &lt;tag&gt;]...   or   @on *",
            example = "@on mobile, tablet\n    menu:\n        height 300 px",
        ),
        "@if" to GalenDoc(
            title = "@if",
            summary = "Runs the indented block when a JavaScript condition holds.",
            syntax = "@if ${'$'}{&lt;javascript&gt;}",
            values = "The condition may call Galen's API: <code>isVisible</code>, " +
                "<code>isPresent</code>, <code>count</code>, <code>find</code>, <code>findAll</code>.",
            example = "@if ${'$'}{isVisible(\"banner\")}\n    banner:\n        width 300 px\n@else\n    " +
                "@die \"no banner\"",
        ),
        "@elseif" to GalenDoc(
            title = "@elseif",
            summary = "A further condition in an <code>@if</code> chain. Must follow an " +
                "<code>@if</code> or <code>@elseif</code> at the same indentation.",
            syntax = "@elseif ${'$'}{&lt;javascript&gt;}",
        ),
        "@else" to GalenDoc(
            title = "@else",
            summary = "The fallback branch of an <code>@if</code> chain. Must follow an " +
                "<code>@if</code> or <code>@elseif</code> at the same indentation.",
        ),
        "@for" to GalenDoc(
            title = "@for",
            summary = "Repeats the indented block over a numeric sequence.",
            syntax = "@for [&lt;sequence&gt;] as &lt;name&gt;",
            values = "The sequence mixes single values and ranges: <code>[1 - 5, 7, 9, 20 - 25]</code>. " +
                "It may come from an expression.",
            example = "@for [1 - 9] as index\n    menu_item-${'$'}{index}:\n        " +
                "left-of menu_item-${'$'}{index + 1} 10px",
        ),
        "@forEach" to GalenDoc(
            title = "@forEach",
            summary = "Repeats the indented block over every object matching a pattern, or over an " +
                "object group.",
            syntax = "@forEach [&lt;pattern&gt;] as &lt;name&gt;[, next|prev|index as &lt;name&gt;]",
            values = "<code>next</code> stops before the last item; <code>prev</code> starts at the " +
                "second; <code>index</code> is 1-based.",
            example = "@forEach [menu_item-*] as item, next as nextItem\n    ${'$'}{item}:\n        " +
                "left-of ${'$'}{nextItem} 10px",
        ),
        "@die" to GalenDoc(
            title = "@die",
            summary = "Fails the spec immediately with a message — usually inside an " +
                "<code>@if</code> guarding a precondition.",
            syntax = "@die \"&lt;message&gt;\"",
            values = "The message is <b>required</b> and must be double-quoted.",
            example = "@if ${'$'}{count(\"menu_item-*\") === 0}\n    @die \"There are no menu items\"",
            note = "Since Galen 2.3.",
        ),
    )

    private val KEYWORDS: Map<String, GalenDoc> = buildMap {
        // Sides
        for (side in listOf("left", "right", "top", "bottom")) {
            put(
                side,
                GalenDoc(
                    title = side,
                    summary = "A side. Follows a range to say which edge the distance is measured from.",
                    values = "Galen accepts only <code>left</code>, <code>right</code>, " +
                        "<code>top</code> and <code>bottom</code>, matched case-sensitively.",
                ),
            )
        }

        // Matchers
        put("is", GalenDoc("is", "Requires an exact match of the whole value."))
        put("contains", GalenDoc("contains", "Requires the value to contain the expected text."))
        put("starts", GalenDoc("starts", "Requires the value to begin with the expected text."))
        put("ends", GalenDoc("ends", "Requires the value to end with the expected text."))
        put(
            "matches",
            GalenDoc(
                "matches",
                "Requires the value to match a <b>Java</b> regular expression.",
                example = "text matches \"Welcome .* today\"",
            ),
        )

        // Text operations
        put("lowercase", GalenDoc("lowercase", "Lower-cases the text before comparing. Not supported by <code>css</code>."))
        put("uppercase", GalenDoc("uppercase", "Upper-cases the text before comparing. Not supported by <code>css</code>."))
        put("singleline", GalenDoc("singleline", "Replaces newlines with spaces before comparing."))

        // Alignment
        put(
            "horizontally",
            GalenDoc(
                "horizontally",
                "Aligns along the horizontal axis.",
                values = "Accepts the edges <code>all</code>, <code>top</code>, <code>bottom</code>, " +
                    "<code>centered</code>.",
            ),
        )
        put(
            "vertically",
            GalenDoc(
                "vertically",
                "Aligns along the vertical axis.",
                values = "Accepts the edges <code>all</code>, <code>left</code>, <code>right</code>, " +
                    "<code>centered</code>.",
            ),
        )
        put("all", GalenDoc("all", "Aligns by both opposing edges, or centres on both axes."))
        put("centered", GalenDoc("centered", "Aligns by centre rather than by an edge."))

        // Count filters
        put("any", GalenDoc("any", "Counts every matching object, visible or not."))

        // Modifiers
        put("partly", GalenDoc("partly", "Relaxes containment: only the listed ranges are checked."))
        put("frame", GalenDoc("frame", "Enters an iframe before running the component's specs."))
        put("edge", GalenDoc("edge", "Required separator in <code>on</code>, between the corner and the object."))

        // Locator types
        put("id", GalenDoc("id", "Finds the element by its DOM id."))
        put("css", GalenDoc("css", "Finds the element with a CSS selector. This is the default when the type is omitted."))
        put("xpath", GalenDoc("xpath", "Finds the element with an XPath expression."))

        // Special objects
        put(
            "screen",
            GalenDoc(
                "screen",
                "The whole page area inside the browser, including the part scrolled out of view. " +
                    "Needs no declaration.",
            ),
        )
        put(
            "viewport",
            GalenDoc(
                "viewport",
                "The currently visible area of the page. Needs no declaration — useful for fixed " +
                    "elements that stay put while scrolling.",
            ),
        )
        put("parent", GalenDoc("parent", "Inside a component spec, the element the component was applied to."))
        put("self", GalenDoc("self", "Identical to <code>parent</code>: the component's own element."))
        put(
            "global",
            GalenDoc(
                "global",
                "A pseudo-object for checks not tied to any element, such as <code>count</code>.",
                example = "global:\n    count any menu_item-* is 4",
            ),
        )

        // Range words and units
        put("to", GalenDoc("to", "Separates the bounds of a range: <code>50 to 200 px</code>."))
        put("of", GalenDoc("of", "Introduces a relative reference: <code>100 % of main/width</code>."))
        put("px", GalenDoc("px", "Pixels. The unit for absolute ranges."))
    }
}
