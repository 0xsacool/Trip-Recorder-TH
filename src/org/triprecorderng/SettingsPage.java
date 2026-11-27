package org.triprecorderng;

import android.app.AlertDialog;
import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * The Setting screen: the same cards, switches and buttons as the Tweaks screen, in two columns. What the rows do
 * is unchanged; MainActivity keeps the dialogs and the work behind them and is reached through Host.
 */
final class SettingsPage {
    interface Host {
        void toast(String s);

        /** Draws the page again shortly (something it shows has changed). */
        void rebuildSoon();
        void changeAppearance();

        String languageName();

        void showLanguageDialog();

        String updateText();

        String channelDescription();

        void checkForUpdates();

        void showChannelDialog();

        /** The text views MainActivity refreshes after the update channel changes. */
        void bindUpdateViews(TextView updates, TextView channel);

        void toggleRecorder();

        String vehicleName(String raw);

        void selfTest();

        void toggleLink();

        void reconnect();

        void forgetKey();

        boolean locationAllowed();

        void requestLocation();

        void openAutostart();

        void showPriceDialog();

        void toggleBackup(boolean on);

        void backupNow();

        void confirmRestore();

        void exportCsv();

        /** Sets the code that is run about once a second while the page is shown. */
        void refresher(Runnable r);
    }

    /** Kept across redraws so the page does not jump back to the top when something changes. */
    private static boolean advancedOpen;
    private static int scrollY;

    static void resetScroll() {
        scrollY = 0;
    }

    private final Context c;
    private final Host host;
    private final Kit k;

    private Kit.Row serviceRow;
    private Kit.SwitchView serviceSwitch;
    private Kit.Row sourceRow;
    private Kit.Row linkRow;
    private Kit.SwitchView linkSwitch;
    private Kit.Row reconnectRow;

    static View build(Context c, Host host) {
        return new SettingsPage(c, host).create();
    }

    private SettingsPage(Context c, Host host) {
        this.c = c;
        this.host = host;
        this.k = new Kit(c);
    }

    private View create() {
        final ScrollView sv = new ScrollView(c);
        sv.setVerticalScrollBarEnabled(false);
        LinearLayout cols = new LinearLayout(c);
        cols.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout left = k.column();
        LinearLayout right = k.column();
        cols.addView(left, new LinearLayout.LayoutParams(0, -2, 1f));
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(0, -2, 1f);
        rlp.setMarginStart(k.d(12));
        cols.addView(right, rlp);
        sv.addView(cols, new ViewGroup.LayoutParams(-1, -2));

        generalSection(left);
        recordingSection(left);
        linkSection(right);
        backupSection(right);
        File lf = Diag.file();
        TextView log = k.label(L.f("Log file: %s", lf == null ? "?" : L.ltr(lf.getPath())), 12, Kit.MUTED, Kit.MEDIUM);
        log.setPadding(k.d(6), k.d(12), k.d(6), k.d(12));
        right.addView(log);

        sv.setOnScrollChangeListener(new View.OnScrollChangeListener() {
            @Override public void onScrollChange(View v, int x, int y, int ox, int oy) {
                scrollY = y;
            }
        });
        sv.post(new Runnable() {
            @Override public void run() {
                sv.scrollTo(0, scrollY);
            }
        });
        host.refresher(new Runnable() {
            @Override public void run() {
                refresh();
            }
        });
        refresh();
        return sv;
    }

    // ---- sections --------------------------------------------------------------------------------------
    private void section(LinearLayout into, String title, Kit.Row... rows) {
        TextView h = k.header(title);
        LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(-1, -2);
        hlp.topMargin = k.d(into.getChildCount() == 0 ? 0 : 12);
        into.addView(h, hlp);
        LinearLayout card = k.card();
        k.fill(card, rows);
        into.addView(card, new LinearLayout.LayoutParams(-1, -2));
    }

    private Kit.Row action(String title, String desc, String button, boolean primary, View.OnClickListener l) {
        Kit.Row r = k.buttonRow(title, desc, button, primary);
        r.onClick(l);
        return r;
    }

    private Kit.Row toggle(String title, String desc, Kit.SwitchView sw, View.OnClickListener l) {
        Kit.Row r = k.switchRow(title, desc, sw, false);
        r.onClick(l);
        return r;
    }

    private void generalSection(LinearLayout into) {
        Kit.Row language = action(L.t("Language"), host.languageName(), L.t("Change"), false,
                new View.OnClickListener() {
                    @Override public void onClick(View v) {
                        host.showLanguageDialog();
                    }
                });
        String[] appearanceChoices={L.t("Dark"),L.t("Light"),L.t("Auto (system)")};
        boolean lightSupported=Prefs.lightAppearanceSupported(c);
        Kit.Row appearance=action(L.t("Appearance"),lightSupported?appearanceChoices[Prefs.appearanceMode(c)]:L.t("Dark"),
                lightSupported?L.t("Change"):L.t("Details"),false,v->{
                    if(!Prefs.lightAppearanceSupported(c)){
                        Ui.show(new AlertDialog.Builder(c).setTitle(L.t("Appearance"))
                                .setMessage(L.t("Light and Auto are available with Classic and NISMO GT3."))
                                .setPositiveButton(L.t("OK"),null));
                        return;
                    }
                    new AlertDialog.Builder(c)
                        .setTitle(L.t("Appearance"))
                        .setSingleChoiceItems(appearanceChoices,Prefs.appearanceMode(c),(dialog,which)->{
                            if(Prefs.appearanceMode(c)!=which){
                                Prefs.setAppearanceMode(c,which);
                                dialog.dismiss();
                                host.changeAppearance();
                            }else dialog.dismiss();
                        }).setNegativeButton(L.t("Cancel"),null).show();
                });
        Kit.Row theme = action(L.t("Dashboard theme"),
                Prefs.themeName(c), L.t("Change"), false, new View.OnClickListener() {
                    @Override public void onClick(View v) {
                        final String[] keys = Prefs.dashboardThemes();
                        String[] choices = new String[keys.length];
                        for (int i = 0; i < keys.length; i++) choices[i] = Prefs.themeName(keys[i]);
                        String current = Prefs.dashboardTheme(c);
                        int selected = 0;
                        for (int i = 0; i < keys.length; i++) if (keys[i].equals(current)) selected = i;
                        new AlertDialog.Builder(c).setTitle(L.t("Dashboard theme"))
                                .setSingleChoiceItems(choices, selected, (dialog, which) -> {
                                    boolean wasLight=Prefs.lightAppearance(c);
                                    Prefs.setDashboardTheme(c, keys[which]);
                                    dialog.dismiss();
                                    if(wasLight!=Prefs.lightAppearance(c))host.changeAppearance();
                                    else host.rebuildSoon();
                                })
                                .setNegativeButton(L.t("Cancel"), null).show();
                    }
                });
        Kit.Row refreshMode = action(L.t("Dashboard refresh mode"),
                Prefs.performanceRefresh(c) ? L.t("Performance Mode (250 ms)") : L.t("Eco Mode (1,000 ms)"),
                L.t("Change"), false, v -> new AlertDialog.Builder(c)
                        .setTitle(L.t("Dashboard refresh mode"))
                        .setSingleChoiceItems(new String[]{L.t("Eco Mode (1,000 ms)"), L.t("Performance Mode (250 ms)")},
                                Prefs.performanceRefresh(c) ? 1 : 0, (dialog, which) -> {
                                    Prefs.setPerformanceRefresh(c, which == 1);
                                    dialog.dismiss();
                                    host.rebuildSoon();
                                })
                        .setNegativeButton(L.t("Cancel"), null).show());
        final String[] motionChoices = {"Off", "Reduced", "Full"};
        String motionTitle = Prefs.THEME_NISMO.equals(Prefs.dashboardTheme(c)) ? "Motion effects (NISMO GT3)" : "Motion effects (GT-R)";
        Kit.Row motion = action(motionTitle, motionChoices[Prefs.motionMode(c).ordinal()],
                L.t("Change"), false, v -> new AlertDialog.Builder(c)
                        .setTitle(motionTitle)
                        .setSingleChoiceItems(motionChoices, Prefs.motionMode(c).ordinal(), (dialog, which) -> {
                            Prefs.setMotionMode(c, SharedMotionController.Mode.values()[which]);
                            dialog.dismiss();
                            host.rebuildSoon();
                        }).setNegativeButton(L.t("Cancel"), null).show());
        String[] menuChoices = {L.t("Always Show"), L.t("Tap to Hide"), L.t("Auto Hide (5 seconds)")};
        Kit.Row menu = action(L.t("Dashboard menu"), menuChoices[Prefs.dashboardMenuMode(c)],
                L.t("Change"), false, v -> new AlertDialog.Builder(c)
                        .setTitle(L.t("Dashboard menu"))
                        .setSingleChoiceItems(menuChoices, Prefs.dashboardMenuMode(c), (dialog, which) -> {
                            Prefs.setDashboardMenuMode(c, which);
                            dialog.dismiss();
                            host.rebuildSoon();
                        })
                        .setNegativeButton(L.t("Cancel"), null).show());
        Kit.Row updates = action(L.t("Updates"), host.updateText(), L.t("Update"), true,
                new View.OnClickListener() {
                    @Override public void onClick(View v) {
                        host.checkForUpdates();
                    }
                });
        Kit.Row prices = action(L.t("Energy prices"),
                Prefs.ATTO3_EDITION ? L.per(Ui.f(Cost.elecPrice(c), "%.2f") + " " + Prefs.currency(c), L.t("kWh"))
                        + (Cost.elecIsDefault(c) ? L.t(" (default)") : "") : L.f("Electricity %1$s, fuel %2$s",
                        L.per(Ui.f(Cost.elecPrice(c), "%.4f") + " " + Prefs.currency(c), L.t("kWh"))
                                + (Cost.elecIsDefault(c) ? L.t(" (default)") : ""),
                        L.per(Ui.f(Cost.fuelPrice(c), "%.3f") + " " + Prefs.currency(c), L.t("L"))
                                + (Cost.fuelIsDefault(c) ? L.t(" (default)") : "")),
                L.t("Set"), false, new View.OnClickListener() {
                    @Override public void onClick(View v) {
                        host.showPriceDialog();
                    }
                });
        Kit.Row chargingProfiles=action(L.t("Charging cost profiles"),L.t("Separate AC and DC prices"),L.t("Change"),false,
                v->ChargingCostsPage.manage(c,host::rebuildSoon));
        Kit.Row battery = action(L.t("Nominal battery capacity"), Ui.f(Prefs.nominalBatteryKwh(c), "%.2f") + " kWh", L.t("Set"), false,
                new View.OnClickListener() {
                    @Override public void onClick(View v) { showBatteryDialog(); }
                });
        Kit.Row range = action(L.t("Range calculation"), Prefs.dynamicRange(c) ? RangeEstimate.DYNAMIC : RangeEstimate.EPA,
                L.t("Change"), false, v -> new AlertDialog.Builder(c)
                        .setTitle(L.t("Range calculation"))
                        .setSingleChoiceItems(new String[]{RangeEstimate.EPA, RangeEstimate.DYNAMIC},
                                Prefs.dynamicRange(c) ? 1 : 0, (dialog, which) -> {
                                    Prefs.setDynamicRange(c, which == 1);
                                    dialog.dismiss();
                                    host.rebuildSoon();
                                })
                        .setNegativeButton(L.t("Cancel"), null).show());
        Kit.Row epa = action(L.t("EPA reference at 100%"), Ui.f(Prefs.epaRangeKm(c), "%.0f") + " km",
                L.t("Set"), false, v -> showEpaDialog());
        final Kit.Row channel = action(L.t("Update channel"), host.channelDescription(), L.t("Change"), false,
                new View.OnClickListener() {
                    @Override public void onClick(View v) {
                        host.showChannelDialog();
                    }
                });
        channel.setVisible(advancedOpen);
        final Kit.Row advanced = k.buttonRow(L.t("Advanced"),
                L.t("Options for experts: which builds the update check follows"),
                advancedOpen ? L.t("Hide") : L.t("Show"), false);
        advanced.onClick(new View.OnClickListener() {
            @Override public void onClick(View v) {
                advancedOpen = !advancedOpen;
                channel.setVisible(advancedOpen);
                ((TextView) advanced.control).setText(advancedOpen ? L.t("Hide") : L.t("Show"));
            }
        });
        host.bindUpdateViews(updates.desc, channel.desc);
        section(into, L.t("General"), language, appearance, theme, refreshMode, motion, menu, updates, prices, chargingProfiles, battery, range, epa, advanced, channel);
        if(Prefs.THEME_NISMO.equals(Prefs.dashboardTheme(c))) {
            boolean th=L.TH.equals(L.current());
            String label=th ? "แถบด้านบน NISMO" : "NISMO dashboard header";
            String[] modes=th ? new String[]{"มาตรฐาน","กะทัดรัด","ซ่อนอัตโนมัติ"} :
                    new String[]{"Standard","Compact","Auto Hide"};
            Kit.Row header=action(label,modes[Prefs.nismoHeaderMode(c)],L.t("Change"),false,
                    v->new AlertDialog.Builder(c).setTitle(label).setSingleChoiceItems(modes,Prefs.nismoHeaderMode(c),
                            (dialog,which)->{Prefs.setNismoHeaderMode(c,which);dialog.dismiss();host.rebuildSoon();})
                            .setNegativeButton(L.t("Cancel"),null).show());
            String styleTitle=th ? "รูปแบบมาตรวัด" : "Gauge Motion Style";
            String[] styles={"Standard","Sequential GT3"};
            int selected=Prefs.sequentialGt3(c) ? 1 : 0;
            Kit.Row style=action(styleTitle,styles[selected],L.t("Change"),false,
                    v->new AlertDialog.Builder(c).setTitle(styleTitle).setSingleChoiceItems(styles,selected,
                        (dialog,which)->{Prefs.setSequentialGt3(c,which==1);dialog.dismiss();host.rebuildSoon();}).show());
            String limitsTitle=th ? "ช่วงมาตรวัด GT3" : "GT3 gauge ranges";
            Kit.Row limits=action(limitsTitle,"kW / rpm",L.t("Change"),false,v->showGt3Ranges(limitsTitle,th));
            section(into,"NISMO GT3",header,style,limits);
        }
    }

    private void showGt3Ranges(String title,boolean th){
        LinearLayout form=new LinearLayout(c);form.setOrientation(LinearLayout.VERTICAL);
        int pad=Ui.dp(c,16);form.setPadding(pad,0,pad,0);
        String[] names=th ? new String[]{"กำลังมอเตอร์","รอบมอเตอร์","กำลังรีเจน"} :
                new String[]{"Max motor power","Max motor RPM","Max regenerative power"};
        android.widget.EditText[] fields=new android.widget.EditText[3];
        for(SequentialGt3.Scale scale:SequentialGt3.Scale.values()){
            int i=scale.ordinal();
            android.widget.TextView label=new android.widget.TextView(c);
            label.setText(names[i]+" ("+scale.unit+")");form.addView(label);
            fields[i]=new android.widget.EditText(c);fields[i].setSingleLine();
            fields[i].setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
            fields[i].setText(Integer.toString(Prefs.gt3Limit(c,scale)));form.addView(fields[i]);
        }
        android.widget.ScrollView scroll=new android.widget.ScrollView(c);scroll.addView(form);
        AlertDialog dialog=new AlertDialog.Builder(c).setTitle(title).setView(scroll)
                .setNegativeButton(L.t("Cancel"),null).setNeutralButton(th ? "คืนค่าเริ่มต้น" : "Reset to default",null)
                .setPositiveButton(L.t("Save"),null).create();
        dialog.show();
        dialog.getButton(-3).setOnClickListener(v->{for(SequentialGt3.Scale scale:SequentialGt3.Scale.values())
                fields[scale.ordinal()].setText(Integer.toString(scale.standard));});
        dialog.getButton(-1).setOnClickListener(v->{
            int[] values=new int[3];
            for(SequentialGt3.Scale scale:SequentialGt3.Scale.values()){
                int i=scale.ordinal();
                try{values[i]=Integer.parseInt(fields[i].getText().toString().trim());}catch(NumberFormatException ignored){values[i]=0;}
                if(!scale.valid(values[i])){fields[i].setError(scale.min+" – "+scale.max+" "+scale.unit);return;}
            }
            Prefs.setGt3Limits(c,values);dialog.dismiss();host.rebuildSoon();
        });
    }

    private void showEpaDialog() {
        final android.widget.EditText input = new android.widget.EditText(c);
        input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        input.setText(Ui.f(Prefs.epaRangeKm(c), "%.0f"));
        Ui.show(new android.app.AlertDialog.Builder(c)
                .setTitle(L.t("EPA reference at 100%"))
                .setMessage(L.t("Estimated full-battery range in km (50 to 1500), not a certified EPA rating. Dynamic Range uses the latest five valid trips and falls back to this estimate when history is insufficient."))
                .setView(input)
                .setPositiveButton(L.t("Save"), (dialog, which) -> {
                    try {
                        if (!Prefs.setEpaRangeKm(c, Double.parseDouble(input.getText().toString()))) throw new IllegalArgumentException();
                        host.rebuildSoon();
                    } catch (IllegalArgumentException ex) { host.toast(L.t("Invalid range")); }
                })
                .setNegativeButton(L.t("Cancel"), null));
    }

    private void showBatteryDialog() {
        final android.widget.EditText input = new android.widget.EditText(c);
        input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        input.setText(Ui.f(Prefs.nominalBatteryKwh(c), "%.2f"));
        Ui.show(new android.app.AlertDialog.Builder(c)
                .setTitle(L.t("Nominal battery capacity"))
                .setMessage(L.t("Enter the battery capacity in kWh (10 to 150). Used for Dynamic Range and energy estimates when measured data is unavailable."))
                .setView(input)
                .setPositiveButton(L.t("Save"), (dialog, which) -> {
                    try {
                        if (!Prefs.setNominalBatteryKwh(c, Double.parseDouble(input.getText().toString()))) throw new IllegalArgumentException();
                        host.rebuildSoon();
                    } catch (IllegalArgumentException ex) { host.toast(L.t("Invalid battery capacity")); }
                })
                .setNegativeButton(L.t("Cancel"), null));
    }

    private void recordingSection(LinearLayout into) {
        final RecorderService.Live l = RecorderService.live;
        serviceSwitch = new Kit.SwitchView(c);
        serviceRow = toggle(L.t("Trip recording service"), l.running ? L.t("Running") : L.t("Stopped"),
                serviceSwitch, new View.OnClickListener() {
                    @Override public void onClick(View v) {
                        host.toggleRecorder();
                    }
                });
        final Kit.SwitchView routeSwitch = new Kit.SwitchView(c);
        boolean routeOn = Prefs.recordRoute(c);
        routeSwitch.setOn(routeOn);
        Kit.Row route = toggle(L.t("Record route (GPS track)"),
                routeOn ? L.t("On: trips keep their route on the map")
                        : L.t("Off: new trips store no route and no event positions"),
                routeSwitch, new View.OnClickListener() {
                    @Override public void onClick(View v) {
                        Prefs.setRecordRoute(c, !Prefs.recordRoute(c));
                        host.rebuildSoon();
                    }
                });
        sourceRow = action(L.t("Vehicle data source"), host.vehicleName(l.vehicle), L.t("Self-test"), false,
                new View.OnClickListener() {
                    @Override public void onClick(View v) {
                        host.selfTest();
                    }
                });
        boolean loc = host.locationAllowed();
        Kit.Row location = action(L.t("Location (GPS track)"), loc ? L.t("Allowed") : L.t("Not allowed"),
                L.t("Allow"), !loc, new View.OnClickListener() {
                    @Override public void onClick(View v) {
                        host.requestLocation();
                    }
                });
        int health = Prefs.autoStartHealth(c);
        String autoText = health == Prefs.AUTOSTART_OK
                ? L.t("Started automatically after the last restart")
                : Prefs.autoStartSwitchVerifiedOff(c)
                ? L.t("EV TripLog switch is off. Restart the head unit to verify auto-start.")
                : L.t("Auto-start not verified. Check EV TripLog in 'Disable background Apps' and restart the head unit.");
        Kit.Row autostart = action(L.t("BYD auto-start apps"), autoText, L.t("Auto setup"), false,
                new View.OnClickListener() {
                    @Override public void onClick(View v) {
                        host.openAutostart();
                    }
                });
        Kit.Row distance = action(L.t("Distance calibration"), distanceSummary(), L.t("Set"), false, v -> showDistanceMode());
        section(into, L.t("Trip recording"), serviceRow, distance, route, sourceRow, location, autostart);
    }

    private String distanceSummary() {
        int mode = Prefs.distanceMode(c);
        if (mode == DistanceCalibration.GPS) return L.t("GPS distance");
        if (mode == DistanceCalibration.CALIBRATED)
            return L.t("Calibrated distance") + " (" + Ui.f((Prefs.distanceFactor(c) - 1) * 100, "%+.2f") + "%)";
        return L.t("Vehicle distance");
    }

    private void showDistanceMode() {
        String[] choices = {L.t("Vehicle distance"), L.t("GPS distance"), L.t("Calibrate by tire size"),
                L.t("Calibrate by measured distance")};
        Ui.show(new AlertDialog.Builder(c).setTitle(L.t("Distance calibration"))
                .setItems(choices, (d, which) -> {
                    if (which == 2 || which == 3) showCalibrationInputs(which == 2);
                    else {
                        Prefs.setDistanceMode(c, which == 0 ? DistanceCalibration.VEHICLE : DistanceCalibration.GPS);
                        if (which == 1 && !host.locationAllowed()) host.requestLocation();
                        host.toast(L.t("Distance setting applies to the next trip"));
                        host.rebuildSoon();
                    }
                }).setNegativeButton(L.t("Cancel"), null));
    }

    private void showCalibrationInputs(boolean tires) {
        LinearLayout inputs = new LinearLayout(c);
        inputs.setOrientation(LinearLayout.VERTICAL);
        int pad = (int)(16 * c.getResources().getDisplayMetrics().density);
        inputs.setPadding(pad, 0, pad, 0);
        TextView hint = new TextView(c);
        hint.setText(L.t(tires
                ? "Enter full tire sizes including rim (example 215/55 R18). Check the original size for your car."
                : "Enter a reliable measured distance (GPS or surveyed route) and the distance indicated by the car on the same trip. Prefer 20 km or more."));
        inputs.addView(hint);
        android.widget.EditText first = new android.widget.EditText(c);
        android.widget.EditText second = new android.widget.EditText(c);
        first.setSingleLine(true);
        second.setSingleLine(true);
        first.setHint(L.t(tires ? "Original tire size" : "Measured distance (km)"));
        second.setHint(L.t(tires ? "Current tire size" : "Vehicle distance (km)"));
        if (tires) {
            first.setText(Prefs.originalTire(c));
            second.setText(Prefs.currentTire(c));
        } else {
            first.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
            second.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        }
        TextView firstLabel = new TextView(c), secondLabel = new TextView(c);
        firstLabel.setText(first.getHint());
        secondLabel.setText(second.getHint());
        inputs.addView(firstLabel); inputs.addView(first);
        inputs.addView(secondLabel); inputs.addView(second);
        TextView preview = new TextView(c);
        inputs.addView(preview);
        android.text.TextWatcher watcher = new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                double factor = calibrationFactor(tires, first.getText().toString(), second.getText().toString());
                preview.setText(DistanceCalibration.validFactor(factor)
                        ? L.t("Distance correction") + ": " + Ui.f((factor - 1) * 100, "%+.2f") + "%"
                        : L.t("Invalid input or correction exceeds 20%"));
            }
            public void afterTextChanged(android.text.Editable s) {}
        };
        first.addTextChangedListener(watcher);
        second.addTextChangedListener(watcher);
        watcher.onTextChanged("", 0, 0, 0);
        AlertDialog dialog = new AlertDialog.Builder(c).setTitle(L.t(tires ? "Calibrate by tire size" : "Calibrate by measured distance"))
                .setView(inputs).setNegativeButton(L.t("Cancel"), null).setPositiveButton(L.t("Set"), null).create();
        dialog.show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String a = first.getText().toString().trim(), b = second.getText().toString().trim();
            double factor = calibrationFactor(tires, a, b);
            if (!DistanceCalibration.validFactor(factor)) {
                host.toast(L.t("Invalid input or correction exceeds 20%"));
                return;
            }
            Prefs.setDistanceCalibration(c, factor, tires ? a : null, tires ? b : null);
            dialog.dismiss();
            host.toast(L.t("Distance setting applies to the next trip"));
            host.rebuildSoon();
        });
    }

    private static double calibrationFactor(boolean tires, String a, String b) {
        if (tires) return DistanceCalibration.tires(a, b);
        try { return DistanceCalibration.reference(Double.parseDouble(a.trim().replace(',', '.')),
                Double.parseDouble(b.trim().replace(',', '.'))); }
        catch (NumberFormatException e) { return Double.NaN; }
    }

    private void linkSection(LinearLayout into) {
        linkSwitch = new Kit.SwitchView(c);
        linkRow = toggle(L.t("Vehicle data link"), HelperLauncher.statusText(c), linkSwitch,
                new View.OnClickListener() {
                    @Override public void onClick(View v) {
                        host.toggleLink();
                    }
                });
        reconnectRow = action(L.t("Reconnect"), L.t("Try to connect to the car's data right now"),
                L.t("Retry now"), false, new View.OnClickListener() {
                    @Override public void onClick(View v) {
                        host.reconnect();
                    }
                });
        Kit.Row key = action(L.t("Debugging key"), L.t("This app's key for the car's network debugging"),
                L.t("Forget"), false, new View.OnClickListener() {
                    @Override public void onClick(View v) {
                        host.forgetKey();
                    }
                });
        section(into, L.t("Vehicle data link"), linkRow, reconnectRow, key);
    }

    private void backupSection(LinearLayout into) {
        final Backup.Status bs = Backup.status(c);
        boolean autoBk = Prefs.autoBackup(c);
        Kit.SwitchView sw = new Kit.SwitchView(c);
        sw.setOn(autoBk);
        Kit.Row auto = toggle(L.t("Automatic backup"),
                (autoBk ? L.t("On: every finished trip is also saved to ") : L.t("Off. Backup folder: "))
                        + L.ltr(bs.dir.getPath()) + "\n" + L.f("%d trip file(s)", bs.files)
                        + (bs.newestMs > 0 ? L.sep() + L.f("newest %s",
                        new SimpleDateFormat("d MMM HH:mm", L.dateLocale()).format(new Date(bs.newestMs)))
                        : "")
                        + (bs.publicFolder ? "" : "\n" + L.t("Warning: Documents is not writable, so this copy is "
                        + "deleted if the app is uninstalled")),
                sw, new View.OnClickListener() {
                    @Override public void onClick(View v) {
                        host.toggleBackup(!Prefs.autoBackup(c));
                    }
                });
        Kit.Row now = action(L.t("Back up now"), L.t("Saves any finished trip that has no backup file yet"),
                L.t("Back up"), false, new View.OnClickListener() {
                    @Override public void onClick(View v) {
                        host.backupNow();
                    }
                });
        Kit.Row restore = action(L.t("Restore from backup"),
                L.t("Adds the trips from the backup folder that are missing here (nothing is overwritten)"),
                L.t("Restore"), false, new View.OnClickListener() {
                    @Override public void onClick(View v) {
                        host.confirmRestore();
                    }
                });
        Kit.Row export = action(L.t("Export all trips"), "Documents/TripRecorderNG/trips.csv", L.t("Export"), false,
                new View.OnClickListener() {
                    @Override public void onClick(View v) {
                        host.exportCsv();
                    }
                });
        section(into, L.t("Backup and export"), auto, now, restore, export);
    }

    // ---- keeping the live rows current ------------------------------------------------------------------
    private void refresh() {
        RecorderService.Live l = RecorderService.live;
        serviceSwitch.setOn(l.running);
        serviceRow.setDesc(l.running ? L.t("Running") : L.t("Stopped"));
        sourceRow.setDesc(host.vehicleName(l.vehicle));
        boolean linkOn = Prefs.linkEnabled(c);
        linkSwitch.setOn(linkOn);
        linkRow.setDesc(HelperLauncher.statusText(c));
        reconnectRow.setVisible(linkOn);
    }
}
