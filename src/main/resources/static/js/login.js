(() => {
    "use strict";

    for (const form of document.querySelectorAll("form")) {
        form.addEventListener("submit", () => {
            const button = form.querySelector("button[data-busy]");
            if (!button) return;
            button.disabled = true;
            button.textContent = button.dataset.busy;
        });
    }

    const code = document.querySelector("input[name=code]");
    if (code) {
        const verify = code.form.querySelector("button[type=submit]");
        const update = () => {
            code.value = code.value.replace(/\D/g, "").slice(0, 6);
            verify.disabled = code.value.length !== 6;
        };
        code.addEventListener("input", update);
        update();
    }
})();
