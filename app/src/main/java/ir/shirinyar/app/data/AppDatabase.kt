package ir.shirinyar.app.data

import android.content.Context
import androidx.room.*

@Entity(tableName = "products", indices = [Index(value = ["name"], unique = true)])
data class Product(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val unit: String, // "عدد" or "کیلو"
    val price: Long // price per unit in toman
)

@Entity(tableName = "receipt_items")
data class ReceiptItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val label: String,
    val quantity: Double,
    val unit: String,
    val unitPrice: Long,
    val amount: Long,
    val kind: String = "product" // product, manual, discount
)

@Dao
interface ShopDao {
    @Query("SELECT * FROM products ORDER BY name")
    suspend fun products(): List<Product>

    @Query("SELECT * FROM products WHERE name = :name COLLATE NOCASE LIMIT 1")
    suspend fun productByName(name: String): Product?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveProduct(product: Product): Long

    @Query("DELETE FROM products WHERE id = :id")
    suspend fun deleteProduct(id: Long)

    @Query("SELECT * FROM receipt_items ORDER BY id")
    suspend fun items(): List<ReceiptItem>

    @Insert
    suspend fun addItem(item: ReceiptItem): Long

    @Query("DELETE FROM receipt_items WHERE id = (SELECT MAX(id) FROM receipt_items)")
    suspend fun deleteLastItem()

    @Query("DELETE FROM receipt_items")
    suspend fun clearReceipt()
}

@Database(entities = [Product::class, ReceiptItem::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): ShopDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null
        fun get(context: Context): AppDatabase = INSTANCE ?: synchronized(this) {
            INSTANCE ?: Room.databaseBuilder(
                context.applicationContext, AppDatabase::class.java, "shirinyar.db"
            ).build().also { INSTANCE = it }
        }
    }
}
