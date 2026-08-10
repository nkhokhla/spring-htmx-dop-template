@import gg.jte.Content
@param Content content
@param Content scriptContent = @``

<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>Notes</title>
    <link rel="stylesheet" href="/webjars/basecoat-css/dist/basecoat.cdn.min.css">
    <link rel="stylesheet" href="/css/application.css">
</head>
<body>
<main>
    ${content}
</main>
<script src="/webjars/htmx.org/dist/htmx.min.js"></script>
<script src="/webjars/htmx-ext-sse/dist/sse.min.js"></script>
${scriptContent}
</body>
</html>
