<div>${body}</div>
<#if orgUrl?has_content>
<p><#if orgSite?has_content>${orgSite?html}: </#if><a target="_blank" rel="noopener noreferrer" href="${orgUrl?html}">${orgUrl?html}</a></p>
</#if>
