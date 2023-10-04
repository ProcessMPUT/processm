package processm.miners.causalnet

import org.jetbrains.exposed.sql.Database
import processm.core.log.hierarchical.DBHierarchicalXESInputStream
import processm.core.models.causalnet.CausalNet
import processm.core.models.causalnet.DBSerializer
import processm.dbmodels.models.ComponentTypeDto
import processm.miners.AbstractMinerService
import processm.miners.CalcJob
import processm.miners.DeleteJob
import processm.miners.causalnet.onlineminer.OnlineMiner

/*
GitHub Copilot: The code you provided is written in Kotlin and defines a service class called `CausalNetMinerService`. This class extends an `AbstractMinerService` class and provides implementations for two inner classes called `CalcCNetJob` and `DeleteCNetJob`.

The `CausalNetMinerService` class has a companion object that defines a constant called `QUARTZ_CONFIG` with the value `"quartz-causalnet.properties"`. This constant is used to configure the service with Quartz, a job scheduling library for Java.

The `name` property in `CausalNetMinerService` is an overridden property that returns the string `"Causal net"`. This property is used to display the name of the service in the user interface.

The `CalcCNetJob` inner class is an implementation of a `CalcJob` interface that defines a `mine` method and a `store` method. The `mine` method takes a `DBHierarchicalXESInputStream` object and returns a `CausalNet` object. This method uses an `OnlineMiner` object to process the logs in the input stream and returns the result of the miner. The `store` method takes a `Database` object and a `CausalNet` object and returns a string representation of the serialized `CausalNet` object.

The `DeleteCNetJob` inner class is an implementation of a `DeleteJob` interface that defines a `delete` method. The `delete` method takes a `Database` object and an ID string and deletes the corresponding `CausalNet` object from the database using the `DBSerializer.delete` method.

Overall, this code defines a service class that provides functionality for mining and storing causal nets. The `CalcCNetJob` inner class defines a method for mining causal nets from log data, and the `DeleteCNetJob` inner class defines a method for deleting causal nets from a database. The `CausalNetMinerService` class is designed to be used in a Java project with Maven.
*/
class CausalNetMinerService : AbstractMinerService(
    QUARTZ_CONFIG,
    ComponentTypeDto.CausalNet,
    CalcCNetJob::class.java,
    DeleteCNetJob::class.java
) {
    companion object {
        private const val QUARTZ_CONFIG = "quartz-causalnet.properties"
    }

    override val name: String
        get() = "Causal net"

    class CalcCNetJob : CalcJob<CausalNet>() {
        override fun mine(stream: DBHierarchicalXESInputStream): CausalNet {
            val miner = OnlineMiner()
            for (log in stream) {
                miner.processLog(log)
            }
            return miner.result
        }

        override fun store(database: Database, model: CausalNet): String =
            DBSerializer.insert(database, model).toString()
    }

    class DeleteCNetJob : DeleteJob() {
        override fun delete(database: Database, id: String) = DBSerializer.delete(database, id.toInt())
    }
}




