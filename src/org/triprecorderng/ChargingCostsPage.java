package org.triprecorderng;

import android.app.AlertDialog;
import android.content.Context;
import android.text.InputType;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import java.util.List;
import java.util.UUID;

/** User-editable prices and receipt data. No changes to telemetry or charging detection. */
final class ChargingCostsPage {
    private final Context c;
    private final TripDb db;
    private ChargingCostsPage(Context c) {this.c=c;db=TripDb.get(c);}
    static void manage(Context c,Runnable changed) {new ChargingCostsPage(c).profiles(changed);}
    static void edit(Context c,Charge ch,Runnable changed) {new ChargingCostsPage(c).session(ch,changed);}

    private LinearLayout form() {
        LinearLayout v=new LinearLayout(c);v.setOrientation(LinearLayout.VERTICAL);
        v.setPadding(Ui.dp(c,24),Ui.dp(c,8),Ui.dp(c,24),Ui.dp(c,12));
        return v;
    }
    private ScrollView scroll(LinearLayout v) {ScrollView s=new ScrollView(c);s.addView(v);return s;}
    private void label(LinearLayout v,String title) {
        TextView t=new TextView(c);t.setText(L.t(title));t.setPadding(0,Ui.dp(c,8),0,0);v.addView(t);
    }
    private EditText field(LinearLayout v,String label,String value,boolean numeric) {
        label(v,label);EditText e=new EditText(c);e.setSingleLine(true);
        e.setInputType(numeric?InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL:InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        e.setText(value);v.addView(e,new LinearLayout.LayoutParams(-1,-2));return e;
    }
    private Spinner types(LinearLayout v,int type,boolean unknown) {
        label(v,"Charging type");
        Spinner s=new Spinner(c);
        String[] items=unknown?new String[]{L.t("Unknown"),"AC","DC"}:new String[]{"AC","DC"};
        ArrayAdapter<String> a=new ArrayAdapter<>(c,android.R.layout.simple_spinner_item,items);
        a.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);s.setAdapter(a);
        s.setSelection(unknown?(type==2?1:type==3?2:0):(type==3?1:0));
        v.addView(s,new LinearLayout.LayoutParams(-1,-2));return s;
    }
    private int type(Spinner s,boolean unknown) {int p=s.getSelectedItemPosition();return unknown?(p==1?2:p==2?3:0):(p==1?3:2);}
    private static String number(double d) {return ChargeBilling.valid(d)?java.math.BigDecimal.valueOf(d).stripTrailingZeros().toPlainString():"";}
    private double parse(EditText e,boolean required) {
        String s=e.getText().toString().trim();
        if(s.isEmpty()&&!required)return Double.NaN;
        try {double n=Double.parseDouble(s);if(ChargeBilling.valid(n)&&n<=1e9)return n;}catch(NumberFormatException ignored){}
        e.setError(L.t("Enter a valid nonnegative number"));throw new IllegalArgumentException();
    }
    private String profileName(EditText e) {
        String s=e.getText().toString().trim();if(!s.isEmpty())return s;
        e.setError(L.t("Enter a profile name"));throw new IllegalArgumentException();
    }
    private String caption(ChargeBilling.Profile p) {
        return p.name+" · "+(p.type==2?"AC":"DC")+" · "+Cost.money(c,p.rate)+"/kWh"+(p.preferred?" · "+L.t("Default"):"");
    }
    private void profiles(Runnable changed) {
        List<ChargeBilling.Profile> ps=db.chargingProfiles();
        String[] rows=new String[ps.size()+1];rows[0]=L.t("Add charging profile");
        for(int i=0;i<ps.size();i++)rows[i+1]=caption(ps.get(i));
        Ui.show(new AlertDialog.Builder(c).setTitle(L.t("Charging cost profiles"))
                .setItems(rows,(d,i)->profile(i==0?null:ps.get(i-1),()->{changed.run();profiles(changed);}))
                .setNegativeButton(L.t("OK"),null));
    }
    private void profile(ChargeBilling.Profile p,Runnable changed) {
        LinearLayout v=form();
        EditText name=field(v,"Profile name",p==null?"":p.name,false);
        Spinner t=types(v,p==null?2:p.type,false);
        EditText rate=field(v,"Price per kWh",p==null?"":number(p.rate),true);
        CheckBox preferred=new CheckBox(c);preferred.setText(L.t("Default for this charging type"));
        preferred.setChecked(p!=null&&p.preferred);v.addView(preferred);
        AlertDialog.Builder b=new AlertDialog.Builder(c).setTitle(L.t(p==null?"Add charging profile":"Edit charging profile"))
                .setView(scroll(v)).setPositiveButton(L.t("Save"),null).setNegativeButton(L.t("Cancel"),null);
        if(p!=null)b.setNeutralButton(L.t("Delete"),(d,w)->Ui.show(new AlertDialog.Builder(c)
                .setMessage(L.t("Delete profile? Previous sessions will keep their prices."))
                .setPositiveButton(L.t("Delete"),(dd,ww)->{db.deleteChargingProfile(p.id);changed.run();})
                .setNegativeButton(L.t("Cancel"),null)));
        AlertDialog dialog=Ui.show(b);
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(w->{
            try {
                db.saveChargingProfile(new ChargeBilling.Profile(p==null?UUID.randomUUID().toString():p.id,
                        profileName(name),type(t,false),parse(rate,true),preferred.isChecked()));
                dialog.dismiss();changed.run();
            }catch(IllegalArgumentException ignored){}catch(RuntimeException failure){
                Diag.log("charging profile save failed",failure);
                Toast.makeText(c,L.t("Unable to save charging cost"),Toast.LENGTH_LONG).show();
            }
        });
    }
    private void session(Charge ch,Runnable changed) {
        if(!ch.ended)return;
        ChargeBilling old=db.effectiveBilling(ch);
        List<ChargeBilling.Profile> ps=db.chargingProfiles();
        ChargeBilling.Profile[] selected={null};
        for(ChargeBilling.Profile p:ps)if(p.id.equals(old.profileId))selected[0]=p;
        LinearLayout v=form();
        Button choose=new Button(c);
        choose.setText(selected[0]==null?L.t("Select charging profile"):selected[0].name);
        v.addView(choose,new LinearLayout.LayoutParams(-1,-2));
        EditText name=field(v,"Profile name",old.name,false);
        Spinner t=types(v,old.type,true);
        EditText rate=field(v,"Price per kWh",number(old.rate),true);
        EditText energy=field(v,"Billed energy (kWh)",number(old.billedKwh),true);
        EditText actual=field(v,"Actual cost",number(old.actualCost),true);
        label(v,"Leave receipt fields blank to use the estimated cost.");
        choose.setOnClickListener(w->{
            String[] names=new String[ps.size()+1];names[0]=L.t("Custom price or new profile");
            for(int i=0;i<ps.size();i++)names[i+1]=caption(ps.get(i));
            Ui.show(new AlertDialog.Builder(c).setTitle(L.t("Select charging profile")).setItems(names,(d,i)->{
                selected[0]=i==0?null:ps.get(i-1);
                if(selected[0]==null){choose.setText(L.t("Custom price or new profile"));return;}
                ChargeBilling.Profile p=selected[0];choose.setText(p.name);name.setText(p.name);
                t.setSelection(p.type==2?1:2);rate.setText(number(p.rate));
            }).setNegativeButton(L.t("Cancel"),null));
        });
        AlertDialog dialog=Ui.show(new AlertDialog.Builder(c).setTitle(L.t("Edit charging cost"))
                .setView(scroll(v)).setPositiveButton(L.t("Save this session"),null)
                .setNeutralButton(L.t("Save and update profile"),null).setNegativeButton(L.t("Cancel"),null));
        View.OnClickListener save=w->{
            boolean update=w==dialog.getButton(AlertDialog.BUTTON_NEUTRAL);
            try {
                int kind=type(t,true);
                String n=name.getText().toString().trim();
                double r=parse(rate,update),e=parse(energy,false),a=parse(actual,false);
                String id=selected[0]==null?"":selected[0].id;
                if(update) {
                    n=profileName(name);
                    if(kind==0){Toast.makeText(c,L.t("Select AC or DC for this profile"),Toast.LENGTH_SHORT).show();return;}
                    if(id.isEmpty())id=UUID.randomUUID().toString();
                }
                ChargeBilling bill=new ChargeBilling(id,n,kind,r,e,a,true);
                ChargeBilling.Profile profile=update?new ChargeBilling.Profile(id,n,kind,r,
                        selected[0]!=null&&selected[0].preferred&&selected[0].type==kind):null;
                db.saveChargeBillingAndProfile(ch.id,bill,profile);
                if(Prefs.autoBackup(c))new Thread(()->Backup.writeCharge(c.getApplicationContext(),ch)).start();
                dialog.dismiss();changed.run();
            }catch(IllegalArgumentException ignored){}catch(RuntimeException failure){
                Diag.log("charge billing save failed",failure);
                Toast.makeText(c,L.t("Unable to save charging cost"),Toast.LENGTH_LONG).show();
            }
        };
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(save);
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(save);
    }
}
