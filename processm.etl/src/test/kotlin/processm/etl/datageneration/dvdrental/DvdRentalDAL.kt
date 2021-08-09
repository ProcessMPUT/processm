package processm.etl.datageneration.dvdrental

import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.`java-time`.datetime

class DvdRentalDAL {

    object Actors : IntIdTable("actor", "actor_id") {
        val firstName = varchar("first_name", 45)
        val lastName = varchar("last_name", 45)
        val lastUpdate = datetime("last_update")
    }

    object Addresses : IntIdTable("address", "address_id") {
        val address = varchar("address", 50)
        val address2 = varchar("address2", 50)
        val district = varchar("district", 20)
//        val cityId = //TODO unknown type("city_id")
        val postalCode = varchar("postal_code", 10)
        val phone = varchar("phone", 20)
        val lastUpdate = datetime("last_update")


        // Foreign/Imported Keys (One to Many)

        val city = reference("city_id", Cities)


        // Referencing/Exported Keys (One to Many)

        // 3 keys.  Not present in object
    }

    object Categories : IntIdTable("category", "category_id") {
        // Database Columns

        val name = varchar("name", 25)
        val lastUpdate = datetime("last_update")
    }

    object Cities : IntIdTable("city", "city_id") {
        // Database Columns

        val city = varchar("city", 50)
//        val country_id = //TODO unknown type("country_id")
        val lastUpdate = datetime("last_update")


        // Foreign/Imported Keys (One to Many)

        val country = reference("country_id", Countries)


        // Referencing/Exported Keys (One to Many)

        // 1 keys.  Not present in object
    }

    object Countries : IntIdTable("country", "country_id") {
        // Database Columns

//        val country_id = integer("country_id").autoIncrement().primaryKey()
        val country = varchar("country", 50)
        val lastUpdate = datetime("last_update")

        // Referencing/Exported Keys (One to Many)

        // 1 keys.  Not present in object
    }

    object Customers : IntIdTable("customer", "customer_id") {
        // Database Columns

//        val customer_id = integer("customer_id").autoIncrement().primaryKey()
//        val store_id = //TODO unknown type("store_id")
        val firstName = varchar("first_name", 45)
        val lastName = varchar("last_name", 45)
        val email = varchar("email", 50)
//        val address_id = //TODO unknown type("address_id")
        val activebool = bool("activebool")
        val createDate = datetime("create_date")
        val lastUpdate = datetime("last_update")
        val active = integer("active")


        // Foreign/Imported Keys (One to Many)

        val address = reference("address_id", Addresses)
        val store = reference("store_id", Stores)

        // Referencing/Exported Keys (One to Many)

        // 2 keys.  Not present in object
    }

    object Films : IntIdTable("film", "film_id") {
        // Database Columns

//        val film_id = integer("film_id").autoIncrement().primaryKey()
        val title = varchar("title", 255)
        val description = text("description")
        val releaseYear = integer("release_year")
//        val languageId = //TODO unknown type("language_id")
        val rentalDuration = short("rental_duration")
        val rentalRate = decimal("rental_rate", 4, 2)
        val length = short("length")
        val replacementCost = decimal("replacement_cost", 5, 2)
        val rating = varchar("rating", 2147483647)
        val lastUpdate = datetime("last_update")
        val specialFeatures = text("special_features")
//        val fulltext = //TODO unknown type("fulltext")


        // Foreign/Imported Keys (One to Many)

        val language = reference("language_id", Languages)


        // Referencing/Exported Keys (One to Many)

        // 1 keys.  Not present in object
    }

    object FilmActors : IntIdTable("film_actor") {
        // Database Columns
        val actorId = reference("user_id", Actors)
        val filmId = reference("user_group_id", Films)
        override val primaryKey = PrimaryKey(actorId, filmId)
        override val id: Column<EntityID<Int>>
            get() = filmId
        val lastUpdate = datetime("last_update")
    }

    object FilmCategories : IntIdTable("film_category") {
        // Database Columns

        val filmId = reference("film_id", Films)
        val categoryId = reference("category_id", Categories)
        override val primaryKey = PrimaryKey(filmId, categoryId)
        override val id: Column<EntityID<Int>>
            get() = filmId
        val lastUpdate = datetime("last_update")
    }

    object Inventories : IntIdTable("inventory", "inventory_id") {
        // Database Columns

//        val inventory_id = integer("inventory_id").autoIncrement().primaryKey()
//        val film_id = //TODO unknown type("film_id")
//        val store_id = //TODO unknown type("store_id")
        val lastUpdate = datetime("last_update")


        // Foreign/Imported Keys (One to Many)

        val film = reference("film_id", Films)
        val store = reference("store_id", Stores)

        // Referencing/Exported Keys (One to Many)

        // 1 keys.  Not present in object
    }

    object Languages : IntIdTable("language", "language_id") {
        // Database Columns

//        val language_id = integer("language_id").autoIncrement().primaryKey()
        val name = char("name",20)
        val lastUpdate = datetime("last_update")


        // Referencing/Exported Keys (One to Many)

        // 1 keys.  Not present in object
    }

    object Payments : IntIdTable("payment", "payment_id") {
        // Database Columns

//        val payment_id = integer("payment_id").autoIncrement().primaryKey()
//        val customer_id = //TODO unknown type("customer_id")
//        val staff_id = //TODO unknown type("staff_id")
//        val rental_id = integer("rental_id")
        val amount = decimal("amount", 5, 2)
        val paymentDate = datetime("payment_date")


        // Foreign/Imported Keys (One to Many)

        val customer = reference("customer_id", Customers)
        val rental = reference("rental_id", Rentals)
        val staff = reference("staff_id", Staffs)
    }

    object Rentals : IntIdTable("rental", "rental_id") {
        // Database Columns

//        val rental_id = integer("rental_id").autoIncrement().primaryKey()
        val rentalDate = datetime("rental_date")
//        val inventory_id = integer("inventory_id")
//        val customer_id = //TODO unknown type("customer_id")
        val returnDate = datetime("return_date").nullable()
//        val staff_id = //TODO unknown type("staff_id")
        val lastUpdate = datetime("last_update")


        // Foreign/Imported Keys (One to Many)

        val customer = reference("customer_id", Customers)
        val inventory = reference("inventory_id", Inventories)
        val staff = reference("staff_id", Staffs)


        // Referencing/Exported Keys (One to Many)

        // 1 keys.  Not present in object
    }

    object Staffs : IntIdTable("staff", "staff_id") {
        // Database Columns

//        val staff_id = integer("staff_id").autoIncrement().primaryKey()
        val firstName = varchar("first_name", 45)
        val lastName = varchar("last_name", 45)
//        val address_id = //TODO unknown type("address_id")
        val email = varchar("email", 50)
//        val store_id = //TODO unknown type("store_id")
        val active = bool("active")
        val username = varchar("username", 16)
        val password = varchar("password", 40)
        val lastUpdate = datetime("last_update")
//        val picture = //TODO unknown type("picture")


        // Foreign/Imported Keys (One to Many)

        val address = reference("address_id", Addresses)
        val store = reference("store_id", Stores)


        // Referencing/Exported Keys (One to Many)

        // 3 keys.  Not present in object
    }

    object Stores : IntIdTable("store", "store_id") {
        // Database Columns

//        val store_id = integer("store_id").autoIncrement().primaryKey()
//        val manager_staff_id = //TODO unknown type("manager_staff_id")
//        val address_id = //TODO unknown type("address_id")
        val lastUpdate = datetime("last_update")


        // Foreign/Imported Keys (One to Many)

        val address = reference("address_id", Addresses)
        val manager_staff = reference("manager_staff_id", Staffs)
    }
}
