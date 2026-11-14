@import org.limelightcreatives.auth.user.Role
@import org.limelightcreatives.auth.user.User
@import org.limelightcreatives.auth.web.Page
@import java.util.Locale

@param Page page
@param User user

!{String role = user.role().name().charAt(0) + user.role().name().substring(1).toLowerCase(Locale.ROOT);}

@template.layout(page = page, title = "Account", content = @`
    <h1 class="mb-2 font-display text-2xl font-bold">You’re signed in</h1>
    <p class="mb-8 text-sm opacity-65">
        <span>${user.name()}</span>
        <br>
        <span>${user.email()}</span>
    </p>
    <p class="mb-8 text-sm">Role: <strong>${role}</strong></p>

    @if(user.role() != Role.PARTICIPANT)
        <p class="mb-8 text-sm"><a href="/apps" class="underline">Manage apps</a></p>
    @endif

    @template.tag.form(page = page, action = "/logout", content = @`
        @template.tag.submit(label = "Sign out", busy = "Signing out…")
    `)
`)
