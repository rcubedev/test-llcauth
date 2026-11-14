@import org.limelightcreatives.auth.web.Page

@param Page page
@param String notice = null

@template.layout(page = page, title = "Signed in", content = @`
    <h1 class="mb-2 font-display text-2xl font-bold">You’re signed in</h1>
    <p class="mb-8 text-sm opacity-65">${notice != null ? notice : "We couldn’t apply that invite link."}</p>
    @template.tag.link(href = "/login/continue", label = "Continue")
`)
