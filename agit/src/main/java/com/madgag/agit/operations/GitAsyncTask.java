package com.madgag.agit.operations;

import static android.R.drawable.stat_notify_error;
import static com.google.inject.name.Names.named;
import static java.lang.System.currentTimeMillis;

import java.io.File;
import java.util.concurrent.Future;

import roboguice.util.RoboAsyncTask;
import android.os.Handler;
import android.util.Log;

import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.assistedinject.Assisted;
import com.google.inject.name.Named;
import com.madgag.agit.Progress;
import com.madgag.agit.ProgressListener;
import com.madgag.agit.RepoOpScope;
import com.madgag.agit.operation.lifecycle.OperationLifecycleSupport;

public class GitAsyncTask extends RoboAsyncTask<OpNotification> implements ProgressListener<Progress> {

	public final static String TAG = "GAT";
	
	@Inject @Named("repoOpScope") RepoOpScope scope;
	@Inject Injector injector;
	
	private final GitOperation operation;
	private final OperationLifecycleSupport lifecycleSupport;
	
	private long startTime;

	private Progress latestProgress;

    private final Runnable publishOnUIThreadRunnable = new Runnable() {
        public void run() { publishLatestProgress(); }
    };
	
	@Inject
	public GitAsyncTask(
			@Assisted GitOperation operation,
			@Assisted OperationLifecycleSupport lifecycleSupport) {
		this.operation = operation;
		this.lifecycleSupport = lifecycleSupport;
		handler = new Handler();
	}
	
    @Override
    protected void onPreExecute() {
    	Log.i(TAG, "Starting onPreExecute "+operation);
    	lifecycleSupport.startedWith(new OpNotification(operation.getOngoingIcon(), operation.getTickerText(), "Event title", "Event detail"));
    	startTime = currentTimeMillis();
    }

	public OpNotification call() {
		scope.enter();
		try {			
			scope.seed(Key.get(File.class, named("gitdir-from-operation")), operation.getGitDir());
			injector.injectMembers(operation);
			// create and access scoped objects
			try {
				return operation.execute(this);
			} catch (RuntimeException e) {
				String eventTitle = "Error " + operation.getDescription();
				Log.e(TAG, eventTitle, e);
				String detail = e.getMessage() == null ? e.toString() : e.getMessage();
				return new OpNotification(stat_notify_error, operation.getName() + " failed", eventTitle, detail);
			}

		} finally {
			scope.exit();
		}

	}
	
	@Override
	protected void onSuccess(OpNotification opResult) {
		long duration=currentTimeMillis()-startTime;
		Log.d(TAG, "Completed in "+duration+" ms");
		lifecycleSupport.completed(opResult);
	}
	
	// Called on background thread
	public void publish(Progress... values) {
		latestProgress = values[values.length-1];
		Log.d(TAG, "Got progress to post : "+latestProgress);
        handler.post(publishOnUIThreadRunnable);
		Log.d(TAG, "...posted progress");
	}
	
	protected void publishLatestProgress() {
		Log.d(TAG, "publishLatestProgress() : Calling lifecycle publisher with "+latestProgress+" ...");
		lifecycleSupport.publish(latestProgress);
		Log.d(TAG, "...called lifecycle publisher.");
	}

	public GitOperation getOperation() {
		return operation;
	}

	public Future<Void> getFutureInUse() {
		return future;
	}
	
	@Override
	public String toString() {
		return getClass().getSimpleName()+"["+operation+"]";
	}
}
