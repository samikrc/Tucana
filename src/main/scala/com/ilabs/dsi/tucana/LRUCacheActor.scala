package com.ilabs.dsi.tucana

import java.util._

import akka.actor.Actor
import com.ilabs.dsi.tucana.utils.ConfigManager
import ml.combust.mleap.runtime.frame.Transformer

import scala.collection.mutable

/**
  * An implementation of LRU (Least Frequently Used) cache using singly linked list.
  * @author Udhaya K
  */
class LRUCacheActor extends Actor
{

    import LRUCacheActor._

    val dq = new LinkedList[(String, String)]()
    val size = ConfigManager.get("model.cache.size").toInt
    val lruCache = new mutable.HashMap[(String, String), (Transformer, String, String)]()

    private def rearrangeList(key: (String, String)): Unit =
    {
        dq.remove(key)
        dq.addFirst(key)
    }

    def get(key: (String, String)): Option[(Transformer, String, String)] =
    {
        val modelOutput = lruCache.get(key)
        modelOutput.foreach(value => rearrangeList(key))
        modelOutput
    }

    def put(key: (String, String), value: (Transformer, String, String)): Unit =
    {
        if (lruCache.size >= size)
        {
            val leastAccessedNode = dq.removeLast()
            lruCache.remove(leastAccessedNode)
        }
        dq.addFirst(key)
        lruCache.put(key, value)
    }

    def receive =
    {
        case ModelRequest(key) => sender() ! get(key)
        case SaveModel(key, value) => put(key, value)
    }

}

object LRUCacheActor
{
    case class ModelRequest(key: (String, String))
    case class SaveModel(key: (String, String), value: (Transformer, String, String))
}