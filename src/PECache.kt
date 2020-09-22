import java.util.*
import kotlin.collections.HashMap
import kotlin.collections.LinkedHashMap

class Item(
    val key: String,
    val value: Int,
    val priority: Int,
    val expireTime: Long
)

class LRUCache {

    private val map = hashMapOf<String, Node>()
    private val head: Node = Node("", null)
    private val tail: Node = Node("", null)

    init {
        head.next = tail
        tail.prev = head
    }

    fun get(key: String): Item? {
        if (map.containsKey(key)) {
            val node = map[key]!!
            remove(node)
            addAtEnd(node)
            return node.value
        }

        return null
    }

    fun put(key: String, value: Item) {
        if (map.containsKey(key)) {
            remove(map[key]!!)
        }
        val node = Node(key, value)
        addAtEnd(node)
        map[key] = node
    }

    fun remove(key: String) {
        map[key]?.let {
            remove(it)
        }
    }

    fun isEmpty(): Boolean {
        return head.next == tail
    }

    private fun remove(node: Node) {
        val next = node.next!!
        val prev = node.prev!!
        prev.next = next
        next.prev = prev
    }

    fun pop(): Item? {
        val first = head.next
        return if (first != tail) {
            remove(first!!)
            first.value
        } else null
    }

    private fun addAtEnd(node: Node) {
        val prev = tail.prev!!
        prev.next = node
        node.prev = prev
        node.next = tail
        tail.prev = node
    }

    data class Node(val key: String, val value: Item?) {
        var next: Node? = null
        var prev: Node? = null
    }
}

class PECache constructor(var capacity: Int) {

    private val itemMap: HashMap<String, Item> = HashMap()
    private val itemExpireTimeMap: TreeMap<Long, LinkedList<Item>> = TreeMap()
    private val itemPriorityMap: TreeMap<Int, LRUCache> = TreeMap()
    private var expireTimeArray = emptyArray<Long>()

    private var count = 0

    init {
        if (capacity <= 0) {
            throw IllegalArgumentException("capacity <= 0")
        }
    }

    fun getItem(key: String): Item? {
        val item = itemMap[key]

        // reorder the priority order depend on new LRU
        item?.let {
            val priority = it.priority
            itemPriorityMap[priority]?.apply {
                get(it.key)
            }
        }

        return item
    }

    fun setItem(key: String, value: Int, priority: Int, expireTime: Long) {
        if (expireTime < 0) {
            throw IllegalArgumentException("expireTime <= 0")
        }

        if (count >= capacity) {
            evictItem()
        }

        val itemExpireTime = System.currentTimeMillis() + expireTime
        val item = Item(
            key,
            value,
            priority,
            itemExpireTime
        )

        itemMap[key] = item
        var expireTimeLinkedList = itemExpireTimeMap[itemExpireTime]
        if (expireTimeLinkedList == null) {
            expireTimeLinkedList = LinkedList()
        }
        expireTimeLinkedList.add(item)
        itemExpireTimeMap[itemExpireTime] = expireTimeLinkedList

        expireTimeArray = itemExpireTimeMap.keys.toTypedArray() // O(K) K <= N

        var priorityLruCache = itemPriorityMap[priority]
        if (priorityLruCache == null) {
            priorityLruCache = LRUCache()
        }

        priorityLruCache.put(item.key, item)
        itemPriorityMap[priority] = priorityLruCache

        count++
    }

    fun setMaxItems(maxSize: Int) {
        if (maxSize < 0) {
            throw IllegalArgumentException("maxSize <= 0")
        }

        capacity = maxSize
        while (count > capacity) {
            evictItem()
        }
    }

    fun keys() {
        println(itemMap.keys.toString())
    }

    private fun evictItem() {
        val expireTimeIndex = binarySearchForExpireIndex()
        if (expireTimeIndex >= 0) {
            // Get expired items and delete them in all the maps
            (0..expireTimeIndex).forEach { index -> // O(K) K <= N
                val expireTime = expireTimeArray[index]
                val expireLinkedList = itemExpireTimeMap[expireTime] // O(1)
                itemExpireTimeMap.remove(expireTime) // O(1)

                expireLinkedList?.let {
                    it.iterator().forEach { item ->  // O(K) K <= N
                        itemMap.remove(item.key) // O(1)

                        itemPriorityMap[item.priority]?.apply { // O(1)
                            remove(item.key)

                            if (isEmpty()) {
                                itemPriorityMap.remove(item.priority)
                            }
                        }

                        count--
                    }
                }
            }

            expireTimeArray = itemExpireTimeMap.keys.toTypedArray() // O(K) K <= N

            return
        }

        val lowPriority = itemPriorityMap.firstKey()
        itemPriorityMap[lowPriority]?.apply {
            val item = pop()
            item?.let {
                itemMap.remove(it.key) // O(1)

                if (isEmpty()) { // O(1)
                    itemPriorityMap.remove(lowPriority)
                }

                count--
            }
        }
    }

    private fun binarySearchForExpireIndex(): Int { // O(logN)
        val size = expireTimeArray.size
        val value = System.currentTimeMillis()

        var low = 0
        var high = size - 1

        while (low <= high) {
            val mid = low + ((high - low) / 2)
            if (expireTimeArray[mid] > value) {
                high = mid - 1
            } else {
                if ((mid == size - 1) || (expireTimeArray[mid + 1] > value)) return mid
                else low = mid + 1
            }
        }

        return -1
    }
}

fun main() {
    val cache = PECache(5)
    cache.setItem("A", value = 1, priority = 5, expireTime = 10000)
    cache.setItem("B", value = 2, priority = 15, expireTime = 30000)
    cache.setItem("C", value = 3, priority = 5, expireTime = 10000)
    cache.setItem("D", value = 4, priority = 1, expireTime = 15000)
    cache.setItem("E", value = 5, priority = 5, expireTime = 1500)
    cache.getItem("A")  // makes C the most recently used item
    cache.keys()

    cache.setMaxItems(5)
    cache.keys()

    Thread.sleep(5000)

    cache.setMaxItems(4)
    cache.keys()

    cache.setMaxItems(3)
    cache.keys()

    cache.setMaxItems(2)
    cache.keys()
}