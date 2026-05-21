Java.perform(function() {
    var SnsObject = Java.use("com.tencent.mm.protocal.protobuf.SnsObject");
    console.log("parseFrom overloads:");
    SnsObject.parseFrom.overloads.forEach(function(o) {
        console.log("  " + o.argumentTypes.map(function(t){return t.className}).join(", "));
        o.implementation = function() {
            console.log("[HIT] parseFrom(" + arguments[0] + ")");
            return o.apply(this, arguments);
        };
    });
});
