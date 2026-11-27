package org.triprecorderng;

import android.graphics.Rect;
import android.os.Bundle;
import android.view.View;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityNodeProvider;

/** Five selectable regions in the custom Canvas, using its current letterboxed transform. */
final class NismoInstrumentNodes extends AccessibilityNodeProvider {
    private final PerformanceDashboard view;
    private int focused=-1;
    NismoInstrumentNodes(PerformanceDashboard view){this.view=view;}
    @Override public AccessibilityNodeInfo createAccessibilityNodeInfo(int id) {
        if(id==View.NO_ID) {
            AccessibilityNodeInfo root=AccessibilityNodeInfo.obtain(view);
            view.onInitializeAccessibilityNodeInfo(root);
            for(int i=0;i<5;i++) root.addChild(view,i);
            return root;
        }
        if(id<0||id>=5)return null;
        AccessibilityNodeInfo node=AccessibilityNodeInfo.obtain();
        node.setSource(view,id);node.setParent(view);
        node.setPackageName(view.getContext().getPackageName());
        node.setClassName("android.widget.Button");node.setContentDescription(view.instrumentDescription(id));
        node.setClickable(true);node.setFocusable(true);node.setEnabled(true);node.setAccessibilityFocused(focused==id);
        node.addAction(AccessibilityNodeInfo.ACTION_CLICK);
        node.addAction(focused==id ? AccessibilityNodeInfo.ACTION_CLEAR_ACCESSIBILITY_FOCUS : AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS);
        Rect rect=new Rect();view.instrumentBounds(id).roundOut(rect);
        node.setBoundsInParent(rect);
        int[] pos=new int[2];view.getLocationOnScreen(pos);rect.offset(pos[0],pos[1]);
        node.setBoundsInScreen(rect);node.setVisibleToUser(view.isShown()&&!rect.isEmpty());
        return node;
    }
    @Override public AccessibilityNodeInfo findFocus(int kind) {
        return kind==AccessibilityNodeInfo.FOCUS_ACCESSIBILITY && focused>=0 ? createAccessibilityNodeInfo(focused) : null;
    }
    @Override public boolean performAction(int id,int action,Bundle args) {
        if(id==View.NO_ID)return view.performAccessibilityAction(action,args);
        if(id<0||id>=5)return false;
        if(action==AccessibilityNodeInfo.ACTION_CLICK){view.activateInstrument(id);return true;}
        boolean focus=action==AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS;
        if(!focus && action!=AccessibilityNodeInfo.ACTION_CLEAR_ACCESSIBILITY_FOCUS)return false;
        if(focus && focused>=0 && focused!=id) sendFocus(focused,false);
        focused=focus ? id : -1;
        sendFocus(id,focus);
        return true;
    }
    private void sendFocus(int id,boolean focus) {
        AccessibilityEvent event=AccessibilityEvent.obtain(focus ? AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED :
                AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUS_CLEARED);
        event.setSource(view,id);event.setPackageName(view.getContext().getPackageName());
        event.setClassName("android.widget.Button");event.setContentDescription(view.instrumentDescription(id));
        if(view.getParent()!=null)view.getParent().requestSendAccessibilityEvent(view,event);
    }
}
