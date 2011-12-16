package org.everpeace.akka.routing

/**
 * 
 * @author everpeace _at_ gmail _dot_ com
 * @date 11/12/15
 */

package object routing{
     def minLoadSelectLoadBalancer() = akka.routing.Routing.loadBalancer()
}