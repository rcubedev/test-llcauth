@import org.limelightcreatives.auth.web.Page

@param Page page
@param boolean invite = false
@param String email = null
@param String error = null

@template.layout(page = page, title = "Sign in", content = @`
    @if(invite)
        <h1 class="mb-2 font-display text-3xl font-bold">Join Limelight Creatives</h1>
        <p class="mb-8 text-sm opacity-65">
            You have been invited to join as a staff member! Enter your email and we’ll send you a 6-digit code.
        </p>
    @else
        <h1 class="mb-2 font-display text-3xl font-bold">Sign in</h1>
        <p class="mb-8 text-sm opacity-65">We’ll email you a 6-digit code.</p>
    @endif

    @template.tag.form(page = page, action = "/login", classes = "flex flex-col gap-3", content = @`
        <input type="email" name="email" required maxlength="254" autocomplete="email" placeholder="you@example.com"
               aria-label="Email" value="${email}" class="field">
        @template.tag.submit(label = "Send code", busy = "Sending code…")
        @template.tag.error(message = error)
    `)
`)
