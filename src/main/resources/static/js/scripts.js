// Main Scripts
console.log('Mo Stock Keeper App Loaded');

// Helper to handle CSRF tokens if needed later
function getCsrfToken() {
    return document.querySelector("meta[name='_csrf']").getAttribute("content");
}

function getCsrfHeader() {
    return document.querySelector("meta[name='_csrf_header']").getAttribute("content");
}
