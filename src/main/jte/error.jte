@import org.limelightcreatives.auth.web.Page

@param Page page
@param int status = 500

@template.layout(page = page, title = "Something went wrong", content = @`
    @if(status == 404)
        <h1 class="mb-2 font-display text-2xl font-bold">Page not found</h1>
        <p class="mb-8 text-sm opacity-65">There’s nothing at this address.</p>
    @elseif(status == 403)
        <h1 class="mb-2 font-display text-2xl font-bold">Not allowed</h1>
        <p class="mb-8 text-sm opacity-65">Your account can’t use this page.</p>
    @else
        <h1 class="mb-2 font-display text-2xl font-bold">Something went wrong</h1>
        <p class="mb-8 text-sm opacity-65">
            Please try again. If it keeps happening, reach out to us at inquiries@limelightcreatives.org.
        </p>
    @endif
    @if(page.signedIn())
        @template.tag.link(href = "/account", label = "Back to your account")
    @else
        @template.tag.link(href = "/login", label = "Back to sign in")
    @endif
`)
