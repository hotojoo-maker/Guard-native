var TAG='[FAST]';
Java.perform(function(){
    var A=Java.use('android.app.Activity');
    A.startActivity.overload('android.content.Intent').implementation=function(inz){
        var c=inz.getComponent();
        if(c&&c.getClassName().indexOf('MvvmContactListUI')>=0){
            console.log(TAG+' FOUND');
            var b=inz.getExtras();
            if(b){
                var ks=b.keySet().iterator();
                while(ks.hasNext()){
                    var k=String(ks.next());
                    try{console.log(TAG+' '+k+'='+String(b.get(k)))}catch(e){}
                }
            }
        }
        return this.startActivity(inz);
    };
    console.log(TAG+' ok');
});
