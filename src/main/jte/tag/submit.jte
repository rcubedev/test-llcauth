@param String label
@param String busy

<button type="submit" data-busy="${busy}"
        class="relative w-full border-2 border-[var(--foreground)] bg-[var(--ontik-accent)] px-4 py-2 text-center font-display
               font-bold text-[var(--background)] transition-all duration-150 ease-out translate-x-0 translate-y-0
               shadow-[0_0_0_0_transparent] hover:-translate-x-[2px] hover:-translate-y-[2px] hover:bg-[var(--accent)]
               hover:text-[var(--foreground)] hover:shadow-[6px_6px_0_0_var(--foreground)] active:translate-x-0
               active:translate-y-0 active:shadow-[2px_2px_0_0_var(--foreground)] disabled:pointer-events-none
               disabled:cursor-not-allowed disabled:bg-transparent disabled:text-[var(--foreground)]">${label}</button>
