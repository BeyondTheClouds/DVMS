package dvms.dvms

import org.bbk.AkkaArc.util.NodeRef
import java.util.UUID

/**
 * Created with IntelliJ IDEA.
 * User: jonathan
 * Date: 5/31/13
 * Time: 11:41 AM
 * To change this template use File | Settings | File Templates.
 */

object DvmsModel {


   object DvmsPartition {
      def apply(leader: NodeRef, initiator: NodeRef, nodes: List[NodeRef], state: DvmsPartititionState): DvmsPartition =
         DvmsPartition(leader, initiator, nodes, state, UUID.randomUUID())
   }

   case class DvmsPartition(leader: NodeRef, initiator: NodeRef, nodes: List[NodeRef], state: DvmsPartititionState, id: UUID)


   object DvmsPartititionState {
      case class Created() extends DvmsPartititionState("Created")

      case class Blocked() extends DvmsPartititionState("Blocked")

      case class Growing() extends DvmsPartititionState("Growing")

      case class Destroyed() extends DvmsPartititionState("Destroyed")
   }


   class DvmsPartititionState(val name: String) {

      def getName(): String = name

      def isEqualTo(a: DvmsPartititionState): Boolean = {
         this.name == a.getName
      }

      def isDifferentFrom(a: DvmsPartititionState): Boolean = {
         this.name != a.getName
      }
   }


   case class ComputerSpecification(numberOfCPU: Int, ramCapacity: Int, coreCapacity: Int)

   case class PhysicalNode(ref: NodeRef, machines: List[VirtualMachine], url: String, specs: ComputerSpecification)

   case class VirtualMachine(name: String, cpuConsumption: Double, specs: ComputerSpecification)
}
