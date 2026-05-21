Java.perform(function() {
    var hooks = 0;
    Java.enumerateLoadedClasses({
        onMatch: function(name) {
            var lower = name.toLowerCase();
            if (lower.indexOf("reddot") !== -1 || lower.indexOf("red_dot") !== -1
                || lower.indexOf("badge") !== -1 || lower.indexOf("unread") !== -1
                || lower.indexOf("notifycount") !== -1 || lower.indexOf("dot") !== -1) {
                console.log("[CLASS] " + name);
                hooks++;
            }
        },
        onComplete: function() {
            console.log("[DONE] total=" + hooks);
        }
    });
});
