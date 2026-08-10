@import org.jspecify.annotations.Nullable
@param String text = ""
@param @Nullable String error = null

<form id="note-form"
      hx-post="/notes"
      hx-swap="outerHTML"
      class="flex flex-col gap-2">
    <div class="flex gap-2">
        <input type="text"
               name="text"
               value="${text}"
               placeholder="Write a note…"
               class="input grow"
               autocomplete="off">
        <button type="submit" class="btn btn-primary">Add note</button>
    </div>
    @if(error != null)
        <p class="text-sm text-destructive">${error}</p>
    @endif
</form>
