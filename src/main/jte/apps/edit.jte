@import org.limelightcreatives.auth.oauth.Apps
@import org.limelightcreatives.auth.web.AppForm
@import org.limelightcreatives.auth.web.Page

@param Page page
@param String title
@param AppForm form
@param Apps.App app = null
@param String secret = null
@param boolean saved = false
@param String error = null

@template.layout(page = page, title = title, content = @`
    <a href="/apps" class="mb-6 inline-block text-sm underline">All apps</a>
    <h1 class="mb-6 font-display text-2xl font-bold">${title}</h1>

    @if(secret != null)
        <div class="mb-6 border-2 border-[var(--foreground)] p-3 text-sm" role="status">
            <p class="mb-2 font-bold">Copy the client secret now. It won’t be shown again.</p>
            <code class="block break-all select-all">${secret}</code>
        </div>
    @endif
    @if(saved)
        <p class="mb-6 text-sm" role="status">Saved.</p>
    @endif

    @if(app != null)
        <dl class="mb-6 text-sm">
            <dt class="opacity-65">Client ID</dt>
            <dd><code class="break-all select-all">${app.clientId()}</code></dd>
        </dl>
    @endif

    @template.tag.form(page = page, action = app != null ? "/apps/" + app.id() : "/apps", classes = "flex flex-col gap-4", content = @`
        <label class="flex flex-col gap-1 text-sm">
            Name
            <input type="text" name="name" required maxlength="200" value="${form.name()}" class="field">
        </label>

        <fieldset class="flex flex-col gap-1 text-sm" disabled="${app != null}">
            <legend class="mb-1">Type</legend>
            <label class="flex gap-2">
                <input type="radio" name="publicClient" value="false" checked="${!form.publicClient()}">
                Server app, which keeps a secret
            </label>
            <label class="flex gap-2">
                <input type="radio" name="publicClient" value="true" checked="${form.publicClient()}">
                Browser or mobile app, with no secret
            </label>
        </fieldset>

        <label class="flex flex-col gap-1 text-sm">
            Redirect URLs, one per line
            <textarea name="redirectUrls" required rows="3" class="field"
                      placeholder="https://app.example/login/oauth2/code/limelight">${form.redirectUrls()}</textarea>
        </label>

        <label class="flex flex-col gap-1 text-sm">
            Sign-out redirect URLs, one per line
            <textarea name="signOutUrls" rows="2" class="field" placeholder="https://app.example/">${form.signOutUrls()}</textarea>
        </label>

        <label class="flex flex-col gap-1 text-sm">
            ID token signature
            <select name="idTokenAlgorithm" class="field">
                <option value="RS256" selected="${"RS256".equals(form.idTokenAlgorithm())}">RS256 (works with every app)</option>
                <option value="ES256" selected="${"ES256".equals(form.idTokenAlgorithm())}">ES256</option>
            </select>
        </label>

        @template.tag.submit(label = app != null ? "Save" : "Add app", busy = "Saving…")
        @template.tag.error(message = error)
    `)

    @if(app != null)
        @if(!app.settings().publicClient())
            @template.tag.form(page = page, action = "/apps/" + app.id() + "/secret", classes = "mt-8", content = @`
                <p class="mb-3 text-sm opacity-65">A new secret replaces the old one straight away.</p>
                @template.tag.submit(label = "New secret", busy = "Making…")
            `)
        @endif
        @template.tag.form(page = page, action = "/apps/" + app.id() + "/delete", classes = "mt-8", content = @`
            <p class="mb-3 text-sm opacity-65">Deleting the app signs everyone out of it.</p>
            <label class="mb-3 flex gap-2 text-sm"><input type="checkbox" required> Yes, delete it</label>
            @template.tag.submit(label = "Delete app", busy = "Deleting…")
        `)
    @endif
`)
