@import org.limelightcreatives.auth.oauth.Apps
@import org.limelightcreatives.auth.web.Page
@import java.util.List

@param Page page
@param List<Apps.App> apps

@template.layout(page = page, title = "Apps", content = @`
    <a href="/account" class="mb-6 inline-block text-sm underline">Account</a>
    <h1 class="mb-2 font-display text-2xl font-bold">Apps</h1>
    <p class="mb-8 text-sm opacity-65">Apps that can sign people in with their Limelight Creatives account.</p>

    @if(apps.isEmpty())
        <p class="mb-8 text-sm">No apps yet.</p>
    @else
        <ul class="mb-8 flex flex-col gap-3 text-sm">
            @for(Apps.App app : apps)
                <li>
                    <a href="/apps/${app.id()}" class="font-bold underline">${app.settings().name()}</a>
                    <br>
                    <code class="break-all opacity-65">${app.clientId()}</code>
                </li>
            @endfor
        </ul>
    @endif

    @template.tag.link(href = "/apps/new", label = "Add an app")
`)
