/**
 * 
 */
package org.eclipse.jdt.junit.tests;

import java.util.ArrayList;

import junit.framework.Test;
import junit.framework.TestCase;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.Path;

import org.eclipse.core.resources.IncrementalProjectBuilder;
import org.eclipse.core.resources.ResourcesPlugin;

import org.eclipse.swt.widgets.Display;

import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.ILaunchConfigurationWorkingCopy;
import org.eclipse.debug.core.ILaunchManager;
import org.eclipse.debug.core.ILaunchesListener2;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;

import org.eclipse.jdt.internal.junit.launcher.JUnitBaseLaunchConfiguration;

import org.eclipse.jdt.testplugin.JavaProjectHelper;
import org.eclipse.jdt.testplugin.util.DisplayHelper;

import org.eclipse.jdt.junit.launcher.JUnitLaunchShortcut;


public class AbtractTestRunListenerTest extends TestCase {
	
	public static class TestRunLog {
		private ArrayList/*<String>*/ fLog;
		private boolean fIsDone;
		
		public TestRunLog() {
			fLog= new ArrayList();
			fIsDone= false;
		}
		
		public synchronized int getMessageCount() {
			return fLog.size();
		}

		public synchronized String[] getLog() {
			return (String[]) fLog.toArray(new String[fLog.size()]);
		}

		public synchronized void add(String entry) {
			fLog.add(entry);
		}

		public void setDone() {
			fIsDone= true;
		}
		
		public boolean isDone() {
			return fIsDone;
		}
	}
	

	private IJavaProject fProject;
	private boolean fLaunchHasTerminated= false;

	protected void setUp() throws Exception {
		fProject= JavaProjectHelper.createJavaProject("TestRunListenerTest", "bin");
		// have to set up an 1.3 project to avoid requiring a 5.0 VM
		JavaProjectHelper.addRTJar13(fProject);
		JavaProjectHelper.addVariableEntry(fProject, new Path("JUNIT_HOME/junit.jar"), null, null);
	}

	protected void tearDown() throws Exception {
		JavaProjectHelper.delete(fProject);
	}
	
	public static Test setUpTest(Test test) {
		return test;
	}
	
	private static class TestJUnitLaunchShortcut extends JUnitLaunchShortcut {
		public static ILaunchConfiguration createConfiguration(IJavaElement element) throws CoreException {
			ILaunchConfigurationWorkingCopy copy= new TestJUnitLaunchShortcut().createLaunchConfiguration(element);
			return copy.doSave();
		}
	}

	protected IType createType(String source, String packageName, String typeName) throws CoreException, JavaModelException {
		IPackageFragmentRoot root= JavaProjectHelper.addSourceContainer(fProject, "src");
		IPackageFragment pack= root.createPackageFragment(packageName, true, null);
		ICompilationUnit aTestCaseCU= pack.createCompilationUnit(typeName, source, true, null);
		IType aTestCase= aTestCaseCU.findPrimaryType();
		return aTestCase;
	}

	protected String[] launchJUnit(IType aTestCase, final TestRunLog log) throws CoreException {
		ResourcesPlugin.getWorkspace().build(IncrementalProjectBuilder.FULL_BUILD, null);
		
		ILaunchManager lm = DebugPlugin.getDefault().getLaunchManager();
		lm.removeLaunches(lm.getLaunches());
		ILaunchesListener2 launchesListener= new ILaunchesListener2() {
			public void launchesTerminated(ILaunch[] launches) {
				for (int i= 0; i < launches.length; i++) {
					if (isJUnitLaunch(launches[i]))
						fLaunchHasTerminated= true;
					logLaunch("terminated", launches[i]);
				}
			}
			public void launchesRemoved(ILaunch[] launches) {
				for (int i= 0; i < launches.length; i++) {
					if (isJUnitLaunch(launches[i]))
						fLaunchHasTerminated= true;
					logLaunch("removed   ", launches[i]);
				}
			}
			public void launchesAdded(ILaunch[] launches) {
				for (int i= 0; i < launches.length; i++)
					logLaunch("added     ", launches[i]);
			}
			public void launchesChanged(ILaunch[] launches) {
				for (int i= 0; i < launches.length; i++)
					logLaunch("changed   ", launches[i]);
			}
			private void logLaunch(String action, ILaunch launch) {
				StringBuffer buf= new StringBuffer();
				buf.append(System.currentTimeMillis()).append(" ");
				buf.append("launch ").append(action).append(": ");
				ILaunchConfiguration launchConfiguration= launch.getLaunchConfiguration();
				if (launchConfiguration != null) {
					buf.append(launchConfiguration.getName()).append(": ");
				}
				buf.append(launch);
				if (isJUnitLaunch(launch)) {
					buf.append(" [JUnit]");
				}
				System.out.println(buf);				
			}
		};
		lm.addLaunchListener(launchesListener);
		
		ILaunchConfiguration configuration= TestJUnitLaunchShortcut.createConfiguration(aTestCase);
		try {
			configuration.launch(ILaunchManager.RUN_MODE, null);
			new DisplayHelper() {
				protected boolean condition() {
					return fLaunchHasTerminated;
				}
			}.waitForCondition(Display.getCurrent(), 30 * 1000, 1000);
		} finally {
			lm.removeLaunchListener(launchesListener);
			lm.removeLaunches(lm.getLaunches());
			configuration.delete();
		}
		if (! fLaunchHasTerminated)
			fail("Launch has not terminated");
		
		new DisplayHelper(){
			protected boolean condition() {
				return log.isDone();
			}
		}.waitForCondition(Display.getCurrent(), 5*1000, 100);
		return log.getLog();
	}
	
	private boolean isJUnitLaunch(ILaunch launch) {
		ILaunchConfiguration config= launch.getLaunchConfiguration();
		if (config == null)
			return false;
		
		// test whether the launch defines the JUnit attributes
		String portStr= launch.getAttribute(JUnitBaseLaunchConfiguration.PORT_ATTR);
		String typeStr= launch.getAttribute(JUnitBaseLaunchConfiguration.TESTTYPE_ATTR);
		if (portStr == null || typeStr == null)
			return false;
		
		return true;
	}
	
	protected void assertEqualLog(final String[] expectedSequence, String[] logMessages) {
		StringBuffer actual= new StringBuffer();
		for (int i= 0; i < logMessages.length; i++) {
			actual.append(logMessages[i]).append('\n');
		}
		StringBuffer expected= new StringBuffer();
		for (int i= 0; i < expectedSequence.length; i++) {
			expected.append(expectedSequence[i]).append('\n');
		}
		assertEquals(expected.toString(), actual.toString());
	}	

	
}
