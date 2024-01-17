package processm.core.log

import processm.core.models.petrinet.petrinet

/**
 * The "standard" lifecycle model from IEEE-1849-2016 encoded as a Petri net.
 * See IEEE1849-2016 standard Section 7.2 / Figure 5.
 */
val StandardLifecycle = petrinet {
    P tout "schedule" * "autoskip" // start
    P tin "schedule" tout "assign" * "_withdraw1" * "_manualskip1" * "_pi_abort1"// ready
    P tin "assign" * "reassign" tout "reassign" * "start" * "_withdraw2" * "_manualskip2" * "_pi_abort2" // assigned
    P tin "start" * "resume" tout "complete" * "suspend" * "_pi_abort3" * "_ate_abort1" // in progress
    P tin "suspend" tout "resume" * "_pi_abort4" * "_ate_abort2" // suspended
    P tin "_withdraw1" * "_withdraw2" tout "withdraw"
    P tin "_manualskip1" * "_manualskip2" tout "manualskip"
    P tin "_pi_abort1" * "_pi_abort2" * "_pi_abort3" * "_pi_abort4" tout "pi_abort"
    P tin "_ate_abort1" * "_ate_abort2" tout "ate_abort"
    P tin "autoskip" * "withdraw" * "manualskip" * "pi_abort" * "ate_abort" * "complete" // end
}
