package app.yoru.anime;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.os.SystemClock;
import android.view.*;
import android.view.accessibility.AccessibilityNodeInfo;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import static org.junit.Assert.*;

final class DesignChecks {
 static final Instrumentation I=InstrumentationRegistry.getInstrumentation();
 static void onUi(Runnable action){I.runOnMainSync(action);}
 static void await(BooleanSupplier condition,long timeout)throws Exception{long end=SystemClock.uptimeMillis()+timeout;AtomicBoolean ready=new AtomicBoolean();while(SystemClock.uptimeMillis()<end){onUi(()->ready.set(condition.getAsBoolean()));if(ready.get())return;SystemClock.sleep(200);}throw new AssertionError("Expected Android UI state was not reached in "+timeout+" ms");}
 static void screenshot(String name)throws IOException{SystemClock.sleep(350);Bitmap bitmap=I.getUiAutomation().takeScreenshot();assertNotNull(bitmap);File folder=new File(I.getTargetContext().getExternalFilesDir(null),"qa");folder.mkdirs();try(FileOutputStream out=new FileOutputStream(new File(folder,name))){bitmap.compress(Bitmap.CompressFormat.PNG,100,out);}bitmap.recycle();}
 static void tapChoice(String text)throws Exception{long deadline=SystemClock.uptimeMillis()+8000;while(SystemClock.uptimeMillis()<deadline){AccessibilityNodeInfo root=I.getUiAutomation().getRootInActiveWindow();if(root!=null)for(AccessibilityNodeInfo node:root.findAccessibilityNodeInfosByText(text)){if(!text.contentEquals(node.getText()==null?"":node.getText()))continue;AccessibilityNodeInfo candidate=node;for(int i=0;i<4&&candidate!=null;i++,candidate=candidate.getParent())if(candidate.isClickable()&&candidate.performAction(AccessibilityNodeInfo.ACTION_CLICK))return;}SystemClock.sleep(200);}throw new AssertionError("Dialog choice not found: "+text);}
 static void target(View view){Rect visible=new Rect();assertTrue("Target should be on screen: "+view.getContentDescription(),view.getGlobalVisibleRect(visible));int minimum=Ui.dp(view.getContext(),48);assertTrue("Target height must be at least 48dp",visible.height()>=minimum-1);assertTrue("Target width must be at least 48dp",visible.width()>=minimum-1);}
 static View findText(View view,String value){if(view instanceof android.widget.TextView&&value.contentEquals(((android.widget.TextView)view).getText()))return view;if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++){View result=findText(group.getChildAt(i),value);if(result!=null)return result;}}return null;}
 static View findDescription(View view,String value){if(value.contentEquals(view.getContentDescription()==null?"":view.getContentDescription()))return view;if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++){View result=findDescription(group.getChildAt(i),value);if(result!=null)return result;}}return null;}
 static void disjoint(View first,View second){assertNotNull(first);assertNotNull(second);Rect a=new Rect(),b=new Rect();assertTrue(first.getGlobalVisibleRect(a));assertTrue(second.getGlobalVisibleRect(b));assertFalse("Controls must not overlap",Rect.intersects(a,b));}
}
