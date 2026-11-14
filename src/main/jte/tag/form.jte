@import gg.jte.Content
@import org.limelightcreatives.auth.web.Page

@param Page page
@param String action
@param String classes = null
@param Content content

<form method="post" action="${action}" class="${classes}">
    @if(page.csrf() != null)
        <input type="hidden" name="${page.csrf().getParameterName()}" value="${page.csrf().getToken()}">
    @endif
    ${content}
</form>
