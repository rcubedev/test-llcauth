@import gg.jte.Content
@import org.limelightcreatives.auth.web.Page

@param Page page
@param String title
@param Content content

<!DOCTYPE html>
<html lang="en" class="h-full antialiased">
    <head>
        <meta charset="utf-8">
        <meta name="viewport" content="width=device-width, initial-scale=1">

        <title>${title} · Limelight Creatives</title>
        <meta name="robots" content="noindex">

        <meta name="theme-color" content="#fffdf5">
        <link rel="icon" href="${page.asset("/favicon.ico")}">

        <link rel="preload" href="${page.asset("/fonts/classica/Classica-Bold.woff2")}" as="font" type="font/woff2" crossorigin>
        <link rel="preload" href="${page.asset("/fonts/hanken-grotesk/HankenGrotesk-Regular.woff2")}" as="font" type="font/woff2" crossorigin>

        <link rel="stylesheet" href="${page.asset("/css/app.css")}">
        <script src="${page.asset("/js/login.js")}" defer></script>
    </head>
    <body class="min-h-full">
        <main class="flex min-h-[100dvh] items-center justify-center px-6">
            <div class="relative w-full max-w-sm">
                <div class="absolute inset-0 translate-x-3 translate-y-3 bg-[var(--ontik-accent)] md:translate-x-4 md:translate-y-4"></div>

                <div class="relative border-2 border-black bg-[var(--background)] p-8">
                    ${content}

                    @template.tag.sparkle(classes = "pointer-events-none absolute -top-12 -right-10 h-14 w-9 rotate-[16deg] text-[var(--ontik-accent)]")
                    @template.tag.sparkle(classes = "pointer-events-none absolute -top-8 right-0 h-6 w-4 rotate-[-6deg] text-[#1B2340]")
                    @template.tag.sparkle(classes = "pointer-events-none absolute -bottom-10 -left-5 h-9 w-6 rotate-[-15deg] text-[#1B2340]")
                </div>
            </div>
        </main>
    </body>
</html>
