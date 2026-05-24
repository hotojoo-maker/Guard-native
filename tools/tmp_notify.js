Java.perform(function() {
    Java.choose("kc5.v0", {
        onMatch: function(instance) {
            console.log("[FRIDA] kc5.v0 found, calling notifyDataSetChanged()");
            instance.notifyDataSetChanged();
            console.log("[FRIDA] done");
        },
        onComplete: function() {
            console.log("[FRIDA] choose complete");
        }
    });
});
