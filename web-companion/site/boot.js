/* Convo Chat Web — early boot script.
 * Runs before the app renders so the saved theme is applied without a flash,
 * and so failures during startup are shown instead of a blank screen. */
(function () {
  "use strict";
  var root = document.documentElement;
  try {
    var saved = JSON.parse(localStorage.getItem("convo.web.settings") || "{}");
    if (saved && saved.theme) root.setAttribute("data-theme", saved.theme);
    if (saved && saved.density === "compact") root.setAttribute("data-density", "compact");
    if (saved && saved.fontScale) root.style.setProperty("--font-scale", String(saved.fontScale));
  } catch (_) {
    try { localStorage.removeItem("convo.web.settings"); } catch (__) {}
  }
  // Instant paint of the correct system accent when the user picked "system".
  root.setAttribute("data-ready", "0");
  window.addEventListener("error", function (event) {
    var node = document.getElementById("boot-error");
    if (!node) {
      node = document.createElement("div");
      node.id = "boot-error";
      node.className = "boot-error";
      document.body && document.body.appendChild(node);
    }
    node.textContent = "Convo Chat Web hit a problem: " + (event && event.message ? event.message : "unknown error") +
      " — reload the page. If it keeps happening, tell your admin.";
  });
})();
