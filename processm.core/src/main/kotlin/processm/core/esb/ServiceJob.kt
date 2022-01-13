package processm.core.esb

import org.quartz.DisallowConcurrentExecution
import org.quartz.Job

/**
 * A Quartz job that must not have multiple instances executed concurrently. The implementations of this interface must
 * be public classes.
 * @see [DisallowConcurrentExecution]
 */
@DisallowConcurrentExecution
interface ServiceJob : Job
