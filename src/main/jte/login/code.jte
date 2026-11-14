@import org.limelightcreatives.auth.web.Page

@param Page page
@param String email
@param String warning = null
@param String error = null

@template.layout(page = page, title = "Enter your code", content = @`
    <h1 class="mb-2 font-display text-2xl font-bold">Enter your code</h1>
    <p class="mb-8 text-sm opacity-65">
        ${warning != null ? "For" : "Sent to"} <span>${email}</span>. It expires in 10 minutes.
    </p>
    @if(warning != null)
        <p class="-mt-4 mb-6 border-2 border-dashed border-[var(--foreground)] p-3 text-xs" role="status">${warning}</p>
    @endif

    @template.tag.form(page = page, action = "/login/code", classes = "flex flex-col gap-3", content = @`
        <input type="text" name="code" required inputmode="numeric" pattern="[0-9]{6}" maxlength="6"
               autocomplete="one-time-code" placeholder="000000" aria-label="6-digit code" autofocus
               class="field text-center text-lg tracking-[0.5em]">
        @template.tag.submit(label = "Verify", busy = "Checking…")
        @template.tag.error(message = error)
    `)

    <a href="/login?resume" class="mt-3 block text-center text-xs underline opacity-60">Use a different email</a>
`)
