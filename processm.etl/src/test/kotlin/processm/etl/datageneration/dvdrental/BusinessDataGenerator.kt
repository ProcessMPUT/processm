package processm.etl.datageneration.dvdrental

import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.javatime.CurrentDateTime
import org.jetbrains.exposed.sql.transactions.transaction
import processm.logging.loggedScope
import java.math.BigDecimal
import java.sql.DriverManager
import java.util.*
import kotlin.random.Random
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.time.ExperimentalTime

@ExperimentalTime
@Ignore("Exemplary use cases, not real tests")
class BusinessDataGenerator {
    // PostgreSQL DvdRental
    val externalDataSourceConnectionString = ""

    private val customers = mutableSetOf<EntityID<Int>>()
    private val stores = mutableSetOf<EntityID<Int>>()
    private val addresses = mutableSetOf<EntityID<Int>>()
    private val activeStaffers = mutableSetOf<EntityID<Int>>()
    private val staffersOnVacation = mutableSetOf<EntityID<Int>>()
    private val availableInventories = mutableSetOf<EntityID<Int>>()
    private val rentedInventories = mutableMapOf<EntityID<Int>, EntityID<Int>>()
    private val rentals = mutableMapOf<EntityID<Int>, EntityID<Int>>()
    private val films = mutableSetOf<EntityID<Int>>()
    private val languages = mutableSetOf<EntityID<Int>>()
    private val countries = mutableSetOf<EntityID<Int>>()
    private val cities = mutableSetOf<EntityID<Int>>()
    private val managers = mutableSetOf<EntityID<Int>>()

    private val businessProcessExecutionFrequencies: Map<() -> Unit, Int> = mapOf(
        ::addCustomer to 20,
        ::deactivateCustomer to 2,
        ::addRental to 50,
        ::extendRental to 15,
        ::endRental to 45,
        ::addInventory to 30,
        ::addFilm to 10,
        ::addLanguage to 1,
        ::addCountry to 1,
        ::addCity to 1,
        ::addAddress to 5,
        ::addStaff to 5,
        ::startStaffVacation to 2,
        ::endStaffVacation to 2,
        ::addStore to 1,
        ::removeStore to 1
    )
    private val totalFrequency = businessProcessExecutionFrequencies.values.sum()
    private val executedActivitiesCount =
        businessProcessExecutionFrequencies.keys.map { it to 0 }.toMap().toMutableMap()

    @Test
    fun `generate initial business data`() {
        repeat(2) { addCountry() }
        repeat(5) { addCity() }
        repeat(10) { addAddress() }
        repeat(2) { addLanguage() }
        repeat(10) { addFilm() }
        repeat(20) { addInventory() }
        repeat(5) { addStaff() }
        repeat(2) { addStore() }
    }

    @Test
    fun `generate business data`() = loggedScope { logger ->
        `generate initial business data`()
        repeat(400) activity@{
            val randomActivity = Random.nextInt(totalFrequency)
            var frequencySum = 0

            businessProcessExecutionFrequencies.forEach { (activity, frequency) ->
                frequencySum += frequency

                try {
                    if (frequencySum >= randomActivity) {
                        activity()
                        executedActivitiesCount.merge(activity, 1, Int::plus)
                        logger.info("#$it $activity has been successfully executed")
                        return@activity
                    }
                } catch (ex: Exception) {
                    logger.error("An error occurred while executing a business activity #$it $activity", ex)
                }
            }
        }

        executedActivitiesCount.forEach { (activity, count) ->
            logger.info("$activity has been executed $count times")
        }
    }

    fun addCustomer(): Unit =
        transaction(Database.connect({ DriverManager.getConnection(externalDataSourceConnectionString) })) {
            val addressId = addresses.randomOrNull() ?: throw NoSuchElementException("AddressId is not available")
            val storeId = stores.randomOrNull() ?: throw NoSuchElementException("StoreId is not available")
            val customerId = DvdRentalDAL.Customers.insertAndGetId {
                it[firstName] = getRandomString()
                it[lastName] = getRandomString()
                it[email] = "${getRandomString()}@example.com"
                it[address] = addressId
                it[store] = storeId
                it[activebool] = true
                it[active] = 1
                it[createDate] = getCurrentTime()
                it[lastUpdate] = getCurrentTime()
            }

            customers.add(customerId)
        }

    fun deactivateCustomer(): Unit =
        transaction(Database.connect({ DriverManager.getConnection(externalDataSourceConnectionString) })) {
            val customerId = customers.randomOrNull() ?: throw NoSuchElementException("CustomerId is not available")
            val updatedCount = DvdRentalDAL.Customers.update({ DvdRentalDAL.Customers.id eq customerId }) {
                it[active] = 0
                it[activebool] = false
            }

            if (updatedCount > 0) customers.remove(customerId)
        }

    @ExperimentalTime
    fun addRental(): Unit =
        transaction(Database.connect({ DriverManager.getConnection(externalDataSourceConnectionString) })) {
            val customerId = customers.randomOrNull() ?: throw NoSuchElementException("CustomerId is not available")
            val staffId = activeStaffers.randomOrNull() ?: throw NoSuchElementException("StaffId is not available")
            val inventoryId =
                availableInventories.randomOrNull() ?: throw NoSuchElementException("InventoryId is not available")
            val rentalId = DvdRentalDAL.Rentals.insertAndGetId {
                it[rentalDate] = getCurrentTime()
                it[inventory] = inventoryId
                it[customer] = customerId
                it[returnDate] = null
                it[staff] = staffId
                it[lastUpdate] = getCurrentTime()
            }

            repeat((0..Random.nextInt(0, 2)).count()) {
                DvdRentalDAL.Payments.insert {
                    it[customer] = customerId
                    it[staff] = staffId
                    it[rental] = rentalId
                    it[amount] = getRandomAmount()
                    it[paymentDate] = getCurrentTime()
                }
            }

            rentals.put(rentalId, customerId)
            availableInventories.remove(inventoryId)
            rentedInventories.put(rentalId, inventoryId)
        }

    fun extendRental(): Unit =
        transaction(Database.connect({ DriverManager.getConnection(externalDataSourceConnectionString) })) {
            val rentalId = rentals.keys.randomOrNull() ?: throw NoSuchElementException("RentalId is not available")
            val staffId = activeStaffers.randomOrNull() ?: throw NoSuchElementException("StaffId is not available")

            repeat((0..Random.nextInt(0, 2)).count()) {
                DvdRentalDAL.Payments.insert {
                    it[customer] = rentals[rentalId]!!
                    it[staff] = staffId
                    it[rental] = rentalId
                    it[amount] = getRandomAmount()
                    it[paymentDate] = getCurrentTime()
                }
            }
        }

    fun endRental(): Unit =
        transaction(Database.connect({ DriverManager.getConnection(externalDataSourceConnectionString) })) {
            val rentalId = rentals.keys.randomOrNull() ?: throw NoSuchElementException("RentalId is not available")
            val staffId = activeStaffers.randomOrNull() ?: throw NoSuchElementException("StaffId is not available")
            val updatedCount = DvdRentalDAL.Rentals.update({ DvdRentalDAL.Rentals.id eq rentalId }) {
                it[returnDate] = getCurrentTime()
                it[lastUpdate] = getCurrentTime()
            }

            if (updatedCount > 0) {
                repeat((0..Random.nextInt(0, 1)).count()) {
                    DvdRentalDAL.Payments.insert {
                        it[customer] = rentals[rentalId]!!
                        it[staff] = staffId
                        it[rental] = rentalId
                        it[amount] = getRandomAmount()
                        it[paymentDate] = getCurrentTime()
                    }
                }

                rentals.remove(rentalId)
                val inventoryId = rentedInventories.remove(rentalId)
                availableInventories.add(inventoryId!!)
            }
        }

    fun addInventory(): Unit =
        transaction(Database.connect({ DriverManager.getConnection(externalDataSourceConnectionString) })) {
            val filmId = films.randomOrNull() ?: throw NoSuchElementException("FilmId is not available")
            val storeId = films.randomOrNull() ?: throw NoSuchElementException("StoreId is not available")
            val inventoryId = DvdRentalDAL.Inventories.insertAndGetId {
                it[film] = filmId
                it[store] = storeId
                it[lastUpdate] = getCurrentTime()
            }

            availableInventories.add(inventoryId)
        }

    fun removeInventory(): Unit =
        transaction(Database.connect({ DriverManager.getConnection(externalDataSourceConnectionString) })) {
            val inventoryId =
                availableInventories.randomOrNull() ?: throw NoSuchElementException("InventoryId is not available")
            val deletedCount = DvdRentalDAL.Inventories.deleteWhere { DvdRentalDAL.Inventories.id eq inventoryId }

            if (deletedCount > 0) availableInventories.remove(inventoryId)
        }

    fun addFilm(): Unit =
        transaction(Database.connect({ DriverManager.getConnection(externalDataSourceConnectionString) })) {
            val languageId = languages.randomOrNull() ?: throw NoSuchElementException("LanguageId is not available")
            val filmId = DvdRentalDAL.Films.insertAndGetId {
                it[title] = getRandomString()
                it[description] = getRandomString(20)
                it[releaseYear] = 2000
                it[language] = languageId
                it[replacementCost] = getRandomAmount()
                it[lastUpdate] = getCurrentTime()
            }

            films.add(filmId)
        }

    fun addLanguage(): Unit =
        transaction(Database.connect({ DriverManager.getConnection(externalDataSourceConnectionString) })) {
            val languageId = DvdRentalDAL.Languages.insertAndGetId {
                it[name] = getRandomString(3)
                it[lastUpdate] = getCurrentTime()
            }

            languages.add(languageId)
        }

    fun addCountry(): Unit =
        transaction(Database.connect({ DriverManager.getConnection(externalDataSourceConnectionString) })) {
            val countryId = DvdRentalDAL.Countries.insertAndGetId {
                it[country] = getRandomString(8)
                it[lastUpdate] = getCurrentTime()
            }

            countries.add(countryId)
        }

    fun addCity(): Unit =
        transaction(Database.connect({ DriverManager.getConnection(externalDataSourceConnectionString) })) {
            val countryId = countries.randomOrNull() ?: throw NoSuchElementException("CountryId is not available")
            val cityId = DvdRentalDAL.Cities.insertAndGetId {
                it[city] = getRandomString()
                it[country] = countryId
                it[lastUpdate] = getCurrentTime()
            }

            cities.add(cityId)
        }

    fun addAddress(): Unit =
        transaction(Database.connect({ DriverManager.getConnection(externalDataSourceConnectionString) })) {
            val cityId = cities.randomOrNull() ?: throw NoSuchElementException("CityId is not available")

            val addressId = DvdRentalDAL.Addresses.insertAndGetId {
                it[address] = getRandomString(10)
                it[address2] = getRandomString(5)
                it[district] = getRandomString(2)
                it[city] = cityId
                it[postalCode] = getRandomString(5)
                it[phone] = getRandomString(9)
                it[lastUpdate] = getCurrentTime()
            }

            addresses.add(addressId)
        }

    fun addStaff(): Unit =
        transaction(Database.connect({ DriverManager.getConnection(externalDataSourceConnectionString) })) {
            val addressId = addresses.randomOrNull() ?: throw NoSuchElementException("AddressId is not available")
            val staffId = DvdRentalDAL.Staffs.insertAndGetId {
                it[firstName] = getRandomString()
                it[lastName] = getRandomString()
                it[address] = addressId
                it[email] = "${getRandomString()}@example.com"
                it[active] = true
                it[username] = getRandomString()
                it[password] = getRandomString(10)
                it[store] = EntityID(1, DvdRentalDAL.Stores)
                it[lastUpdate] = getCurrentTime()
            }

            activeStaffers.add(staffId)
        }

    fun startStaffVacation(): Unit =
        transaction(Database.connect({ DriverManager.getConnection(externalDataSourceConnectionString) })) {
            val staffId = activeStaffers.randomOrNull() ?: throw NoSuchElementException("StaffId is not available")
            val updatedCount = DvdRentalDAL.Staffs.update({ DvdRentalDAL.Staffs.id eq staffId }) {
                it[active] = false
                it[lastUpdate] = getCurrentTime()
            }

            if (updatedCount > 0) {
                activeStaffers.remove(staffId)
                staffersOnVacation.add(staffId)
            }
        }

    fun endStaffVacation(): Unit =
        transaction(Database.connect({ DriverManager.getConnection(externalDataSourceConnectionString) })) {
            val staffId = staffersOnVacation.randomOrNull() ?: throw NoSuchElementException("StaffId is not available")
            val updatedCount = DvdRentalDAL.Staffs.update({ DvdRentalDAL.Staffs.id eq staffId }) {
                it[active] = true
                it[lastUpdate] = getCurrentTime()
            }

            if (updatedCount > 0) {
                staffersOnVacation.remove(staffId)
                activeStaffers.add(staffId)
            }
        }

    fun removeStaff(): Unit =
        transaction(Database.connect({ DriverManager.getConnection(externalDataSourceConnectionString) })) {
            val staffId = activeStaffers.randomOrNull() ?: throw NoSuchElementException("StaffId is not available")
            val deletedCount = DvdRentalDAL.Staffs.deleteWhere { DvdRentalDAL.Staffs.id eq staffId }

            if (deletedCount > 0) activeStaffers.remove(staffId)
        }

    fun addStore(): Unit =
        transaction(Database.connect({ DriverManager.getConnection(externalDataSourceConnectionString) })) {
            val addressId = addresses.randomOrNull() ?: throw NoSuchElementException("AddressId is not available")
            val staffId = activeStaffers.randomOrNull() ?: throw NoSuchElementException("StaffId is not available")
            val storeId = DvdRentalDAL.Stores.insertAndGetId {
                it[address] = addressId
                it[manager_staff] = staffId
                it[lastUpdate] = getCurrentTime()
            }

            stores.add(storeId)
            activeStaffers.remove(staffId)
            managers.add(staffId)
        }

    fun removeStore(): Unit =
        transaction(Database.connect({ DriverManager.getConnection(externalDataSourceConnectionString) })) {
            val storeId = stores.randomOrNull() ?: throw NoSuchElementException("StoreId is not available")
            val deletedCount = DvdRentalDAL.Stores.deleteWhere { DvdRentalDAL.Stores.id eq storeId }

            if (deletedCount > 0) stores.remove(storeId)
        }

    private fun getRandomAmount(): BigDecimal {
        return Random.nextInt(10, 30).toBigDecimal()
    }

    private fun getRandomString(length: Int = 6): String {
        val allowedChars = ('A'..'Z') + ('a'..'z') + ('0'..'9')
        return (1..length)
            .map { allowedChars.random() }
            .joinToString("")
            .lowercase()
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
    }

    private fun getCurrentTime() = CurrentDateTime
}
