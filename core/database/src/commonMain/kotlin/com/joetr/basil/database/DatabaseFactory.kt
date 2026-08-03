package com.joetr.basil.database

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlSchema
import com.joetr.basil.db.BasilDatabase

public data class BasilDataLayer(
    val driver: SqlDriver,
    val database: BasilDatabase,
)

public expect suspend fun createSqlDriver(schema: SqlSchema<QueryResult.AsyncValue<Unit>>): SqlDriver

public suspend fun createBasilDataLayer(): BasilDataLayer {
    val driver = createSqlDriver(BasilDatabase.Schema)
    return BasilDataLayer(driver, BasilDatabase(driver))
}

public suspend fun createBasilDatabase(): BasilDatabase = createBasilDataLayer().database

public fun basilDatabaseFromDriver(driver: SqlDriver): BasilDatabase = BasilDatabase(driver)

internal fun SqlDriver.execPragma(sql: String) {
    executeQuery(
        identifier = null,
        sql = sql,
        mapper = { cursor ->
            while (cursor.next().value) { }
            QueryResult.Unit
        },
        parameters = 0,
    )
}
