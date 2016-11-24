package android.support.v4.content;

import android.support.annotation.NonNull;

import java.util.ArrayDeque;
import java.util.concurrent.Executor;

public abstract class SupportAsyncTask<Params, Progress, Result> extends ModernAsyncTask<Params, Progress, Result>
{
    private static class SerialExecutor implements Executor
    {
        final ArrayDeque<Runnable> mTasks = new ArrayDeque<>();
        Runnable mActive;

        public synchronized void execute(@NonNull final Runnable r)
        {
            mTasks.offer(new Runnable()
            {
                public void run()
                {
                    try
                    {
                        r.run();
                    }
                    finally
                    {
                        scheduleNext();
                    }
                }
            });
            if (mActive == null)
            {
                scheduleNext();
            }
        }

        protected synchronized void scheduleNext()
        {
            if ((mActive = mTasks.poll()) != null)
            {
                THREAD_POOL_EXECUTOR.execute(mActive);
            }
        }
    }

    public static final Executor THREAD_POOL_EXECUTOR = ModernAsyncTask.THREAD_POOL_EXECUTOR;
    public static final Executor SERIAL_EXECUTOR = new SerialExecutor();

    @SafeVarargs
    public final SupportAsyncTask<Params, Progress, Result> executeOnSupportExecutor(Executor executor, Params... params)
    {
        return (SupportAsyncTask<Params, Progress, Result>) executeOnExecutor(executor, params);
    }
}
