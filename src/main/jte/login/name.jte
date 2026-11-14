@import org.limelightcreatives.auth.web.Page

@param Page page
@param String firstName = null
@param String lastName = null
@param String error = null

@template.layout(page = page, title = "Welcome", content = @`
    <h1 class="mb-2 font-display text-2xl font-bold">Welcome to Limelight Creatives!</h1>
    <p class="mb-8 text-sm opacity-65">Let us know your name and you're all set.</p>

    @template.tag.form(page = page, action = "/login/name", classes = "flex flex-col gap-3", content = @`
        <input type="text" name="firstName" required maxlength="100" autocomplete="given-name" placeholder="First name"
               aria-label="First name" value="${firstName}" autofocus class="field">
        <input type="text" name="lastName" required maxlength="100" autocomplete="family-name" placeholder="Last name"
               aria-label="Last name" value="${lastName}" class="field">
        @template.tag.submit(label = "Finish", busy = "Saving…")
        @template.tag.error(message = error)
    `)
`)
