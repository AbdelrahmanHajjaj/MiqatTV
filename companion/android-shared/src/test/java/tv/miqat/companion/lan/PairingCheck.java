package tv.miqat.companion.lan;

import java.util.ArrayList;
import java.util.List;

public final class PairingCheck {
    static final class Memory implements Pairing.Store {
        List<Pairing.Client> saved=new ArrayList<>(); boolean fail;
        public List<Pairing.Client> read(){return new ArrayList<>(saved);}
        public void write(List<Pairing.Client> next)throws Exception {if(fail)throw new Exception("disk");saved=new ArrayList<>(next);}
    }
    interface Task {void run()throws Exception;}
    static void denied(Task task)throws Exception {try {task.run();}catch(IllegalArgumentException|IllegalStateException|SecurityException expected){return;}throw new AssertionError("Accepted invalid exchange");}
    static void check(boolean value){if(!value)throw new AssertionError();}
    public static void main(String[] args)throws Exception {
        Memory store=new Memory();Pairing p=new Pairing(store,300000,2);
        String w=p.open(100),nonce=p.token(),other=p.token();
        denied(()->p.request(other,"Phone",nonce,101));
        denied(()->p.request(w,"Phone\nspoof",nonce,101));
        Pairing.Pending pending=p.request(w,"هاتف",nonce,101);
        check(pending==p.request(w,"هاتف",nonce,102));check(pending.code.equals(Pairing.code(w,nonce)));
        check(p.poll(w,nonce,103)==null);denied(()->p.poll(w,other,103));
        denied(()->p.request(w,"Other phone",other,104));denied(()->p.approve(other,105));
        p.approve(nonce,106);Pairing.Grant grant=p.poll(w,nonce,107);
        check(!p.authenticate(grant.id,grant.secret));check(store.saved.isEmpty());
        denied(()->p.finish(grant.id,other,108));
        store.fail=true;try{p.finish(grant.id,grant.secret,109);throw new AssertionError();}catch(Exception disk){check(disk.getMessage().equals("disk"));}
        check(!p.authenticate(grant.id,grant.secret));store.fail=false;
        p.finish(grant.id,grant.secret,110);check(p.authenticate(grant.id,grant.secret));
        check(!store.saved.get(0).hash.equals(grant.secret));denied(()->p.poll(w,nonce,111));
        Pairing restarted=new Pairing(store,300000,2);check(restarted.authenticate(grant.id,grant.secret));
        check(!restarted.authenticate(grant.id,other));check(!restarted.authenticate(other,grant.secret));
        String w2=p.open(120);p.request(w2,"Other",other,121);p.approve(other,122);
        Pairing.Grant abandoned=p.poll(w2,other,123);check(!p.isOpen(300120));
        denied(()->p.finish(abandoned.id,abandoned.secret,300121));check(p.clients().size()==1);
        String w3=p.open(400000);p.request(w3,"Other",other,400001);p.reject(other,400002);
        denied(()->p.poll(w3,other,400003));
        String w4=p.open(500000);p.request(w4,"Other",other,500001);p.approve(other,500002);
        Pairing.Grant second=p.poll(w4,other,500003);p.finish(second.id,second.secret,500004);
        denied(()->p.open(500005));
        store.fail=true;try{p.revoke(grant.id);throw new AssertionError();}catch(Exception disk){check(p.authenticate(grant.id,grant.secret));}
        store.fail=false;p.revoke(grant.id);check(!p.authenticate(grant.id,grant.secret));check(p.authenticate(second.id,second.secret));
        check(p.clients().size()==1);p.clients().clear();check(p.clients().size()==1);
        System.out.println("PASS: pairing requires approval and completed grant, expiry, isolation, restart, revocation and atomic storage");
    }
}
