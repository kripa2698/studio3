/**
 * Aptana Studio
 * Copyright (c) 2005-2011 by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the GNU Public License (GPL) v3 (with exceptions).
 * Please see the license.html included with this distribution for details.
 * Any modifications to this file must keep this entire header intact.
 */
package com.aptana.scripting;

import net.contentobjects.jnotify.JNotifyException;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Plugin;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.jruby.RubyInstanceConfig;
import org.osgi.framework.BundleContext;

import com.aptana.core.logging.IdeLog;
import com.aptana.core.util.EclipseUtil;
import com.aptana.scripting.keybindings.internal.KeybindingsManager;
import com.aptana.scripting.listeners.ExecutionListenerRegistrant;
import com.aptana.scripting.listeners.FileWatcherRegistrant;
import com.aptana.scripting.model.BundleManager;
import com.aptana.scripting.model.BundleMonitor;
import com.aptana.scripting.model.RunType;

/**
 * The activator class controls the plug-in life cycle
 */
public class ScriptingActivator extends Plugin
{
	public static final String PLUGIN_ID = "com.aptana.scripting"; //$NON-NLS-1$
	private static ScriptingActivator plugin;

	static
	{
		// Fix for: APSTUD-4508 Rubles don't appear to load correctly when Aptana Studio is in a directory with foreign
		// characters.
		// This makes the jruby posix implementation use a java-only implementation which does handle unicode characters
		// properly when on windows.
		RubyInstanceConfig.nativeEnabled = false;
	}

	/**
	 * Context id set by workbench part to indicate they are scripting aware.
	 */
	public static final String SCRIPTING_CONTEXT_ID = "com.aptana.scripting.context"; //$NON-NLS-1$

	/**
	 * Context id set by workbench part to indicate it's an Aptana Editor and make it aware to any generic command.
	 */
	public static final String EDITOR_CONTEXT_ID = "com.aptana.editor.context"; //$NON-NLS-1$

	/**
	 * Returns the shared instance
	 * 
	 * @return the shared instance
	 */
	public static ScriptingActivator getDefault()
	{
		return plugin;
	}

	/**
	 * This returns the default run type to be used by ScriptingEngine and CommandElement.
	 * 
	 * @return
	 */
	public static RunType getDefaultRunType()
	{
		return RunType.CURRENT_THREAD;
	}

	private FileTypeAssociationListener fileTypeListener;

	/**
	 * The constructor
	 */
	public ScriptingActivator()
	{
	}

	/*
	 * (non-Javadoc)
	 * @see org.eclipse.ui.plugin.AbstractUIPlugin#start(org.osgi.framework.BundleContext)
	 */
	public void start(BundleContext context) throws Exception
	{
		super.start(context);

		plugin = this;

		Job startupJob = new Job("Start Ruble bundle manager") //$NON-NLS-1$
		{
			@Override
			protected IStatus run(IProgressMonitor monitor)
			{
				BundleManager manager = BundleManager.getInstance();

				// register file association listener
				fileTypeListener = new FileTypeAssociationListener();
				manager.addBundleVisibilityListener(fileTypeListener);

				// TODO: Make this an extension point so plugins can contribute these
				// grabbing instances register listeners
				FileWatcherRegistrant.getInstance();
				ExecutionListenerRegistrant.getInstance();

				// load all existing bundles automatically, if we're not running
				// unit tests
				if (EclipseUtil.isTesting())
				{
					System.out.println("Not auto-loading bundles since we are running unit tests"); //$NON-NLS-1$
				}
				else
				{
					manager.loadBundles();
				}

				// install key binding Manager
				KeybindingsManager.install();

				// turn on project and file monitoring
				try
				{
					BundleMonitor.getInstance().beginMonitoring();
				}
				catch (JNotifyException e)
				{
					IdeLog.logError(ScriptingActivator.getDefault(),
							Messages.EarlyStartup_Error_Initializing_File_Monitoring, e);
				}

				return Status.OK_STATUS;
			}
		};

		startupJob.schedule();
	}

	/*
	 * (non-Javadoc)
	 * @see org.eclipse.ui.plugin.AbstractUIPlugin#stop(org.osgi.framework.BundleContext)
	 */
	public void stop(BundleContext context) throws Exception
	{
		try
		{
			BundleMonitor.getInstance().endMonitoring();

			KeybindingsManager.uninstall();

			if (fileTypeListener != null)
			{
				fileTypeListener.cleanup();
				BundleManager.getInstance().removeBundleVisibilityListener(fileTypeListener);
				fileTypeListener = null;
			}

			FileWatcherRegistrant.shutdown();
			ExecutionListenerRegistrant.shutdown();

			// FIXME Clean up the bundle manager singleton!
		}
		catch (Exception e)
		{
			// ignore
		}
		finally
		{
			plugin = null;
			super.stop(context);
		}
	}
}
