package Philosophers

import java.util.concurrent.Semaphore
import org.apache.zookeeper._
import scala.util.Random

case class Philosopher(id: Int,
                       hostPort: String,
                       root: String,
                       left: Semaphore,
                       right: Semaphore,
                       seats: Integer) extends Watcher {
  val zk = new ZooKeeper(hostPort, 3000, this)
  val mutex = new Object()
  val path: String = root + "/" + id.toString

  if (zk == null) throw new Exception("ZK is NULL.")

  override def process(event: WatchedEvent): Unit = {
    mutex.synchronized {
      mutex.notify()
    }
  }

  def eat(): Boolean = {
    printf("Philosopher %d is going to eat\n", id)
    mutex.synchronized {
      var created = false
      while (true) {
        if (!created) {
          zk.create(path, Array.emptyByteArray, ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.EPHEMERAL)
          created = true
        }
        val active = zk.getChildren(root, this)
        if (active.size() > seats) {
          zk.delete(path, -1)
          mutex.wait(3000)
          Thread.sleep(Random.nextInt(5)* 100)
          created = false
        } else {
          left.acquire()
          printf("Philosopher %d picked up the left fork\n", id)
          right.acquire()
          printf("Philosopher %d picked up the right fork\n", id)
          Thread.sleep((Random.nextInt(5) + 1) * 1000)
          right.release()
          printf("Philosopher %d put the right fork\n", id)
          left.release()
          printf("Philosopher %d put the loft fork and finished eating\n", id)
          return true
        }
      }
    }
    false
  }

  def think(): Unit = {
    printf("Philosopher %d is thinking\n", id)
    zk.delete(path, -1)
    Thread.sleep((Random.nextInt(5) + 1) * 1000)
  }
}