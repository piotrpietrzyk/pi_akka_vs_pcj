package Akka_Pi.akka_pi;


import akka.actor.*;
import akka.japi.pf.ReceiveBuilder;
import akka.routing.ActorRefRoutee;
import akka.routing.RoundRobinRoutingLogic;
import akka.routing.Routee;
import akka.routing.Router;

import java.util.ArrayList;
import java.util.List;

public class Pi {

  public static void main(String[] args) {

    Pi pi = new Pi();

    int nrOfWorkers = 8;
    int nrOfElements = 1000000;
    int nrOfMessages = 1000;

    pi.calculate(nrOfWorkers, nrOfElements, nrOfMessages);
  }

  static class Calculate {
  }

  static class Work {
    private final int start;
    private final int nrOfElements;

    public Work(int start, int nrOfElements) {
      this.start = start;
      this.nrOfElements = nrOfElements;
    }

    public int getStart() {
      return start;
    }

    public int getNrOfElements() {
      return nrOfElements;
    }
  }

  static class Result {
    private final double value;

    public Result(double value) {
      this.value = value;
    }

    public double getValue() {
      return value;
    }
  }

  static class PiApproximation {
    private final double pi;
    private final long duration;

    public PiApproximation(double pi, long duration) {
      this.pi = pi;
      this.duration = duration;
    }

    public double getPi() {
      return pi;
    }

    public long getDuration() {
      return duration;
    }
  }

  public static class Worker extends AbstractActor {

    private double calculatePiFor(int start, int nrOfElements) {
      double acc = 0.0;
      for (int i = start * nrOfElements; i <= ((start + 1) * nrOfElements - 1); i++) {
        acc += 4.0 * (1 - (i % 2) * 2) / (2 * i + 1);
      }
      return acc;
    }

      @Override
      public Receive createReceive() {
          ReceiveBuilder builder = ReceiveBuilder.create();
          builder.match(Work.class, w -> {
              double result = calculatePiFor(w.getStart(), w.getNrOfElements());
              getSender().tell(new Result(result), getSelf());
          });
          builder.matchAny(m -> unhandled(m));

          return builder.build();
      }
  }

  public static class Master extends AbstractActor {
      private final Router workerRouter;
      List<Routee> routeeList = new ArrayList<>();
    private final int nrOfMessages;
    private final int nrOfElements;

    private double pi;
    private int nrOfResults;
    private final long start = System.currentTimeMillis();

    private final ActorRef listener;

    public Master(
      int nrOfWorkers, 
      int nrOfMessages, 
      int nrOfElements, 
      ActorRef listener) {

      this.nrOfMessages = nrOfMessages;
      this.nrOfElements = nrOfElements;
      this.listener = listener;

        for (int i = 0; i < nrOfWorkers; i++) {
            ActorRef f = getContext().actorOf(Props.create(Worker.class));
            getContext().watch(f);
            routeeList.add(new ActorRefRoutee(f));
        }

      workerRouter = new Router(new RoundRobinRoutingLogic(), routeeList);

        for (int i = 0; i < nrOfMessages; i++) {
            workerRouter.route(new Work(i, nrOfElements), getSelf());
        }
    }

      @Override
      public Receive createReceive() {
          ReceiveBuilder builder = ReceiveBuilder.create();
           builder.match(Calculate.class, c -> {
               for (int i = 0; i < nrOfMessages; i++) {
                   workerRouter.route(new Work(i, nrOfElements), getSelf());
               }
           });
           builder.match(Result.class, r -> {
               pi += r.getValue();
               nrOfResults += 1;
               if (nrOfResults == nrOfMessages) {

                   long duration = System.currentTimeMillis() - start;
                   listener.tell(new PiApproximation(pi, duration), getSelf());

                   getContext().stop(getSelf());
               }
           });

           builder.matchAny(o -> unhandled(o));

          return builder.build();
      }
  }

  public static class Listener extends AbstractActor {

      @Override
      public Receive createReceive() {
          ReceiveBuilder builder = ReceiveBuilder.create();
          builder.match(PiApproximation.class, p -> {
              System.out.println(p.getPi()+ " " + p.getDuration() * 1.0E-3);
          });
          builder.matchAny(o -> unhandled(o));

          return builder.build();
      }
  }

  public void calculate(
    final int nrOfWorkers, 
    final int nrOfElements, 
    final int nrOfMessages) {

    ActorSystem system = ActorSystem.create("PiSystem");

    final ActorRef listener = system.actorOf(Props.create(Listener.class), "listener");

    @SuppressWarnings("serial")
    ActorRef master = system.actorOf(Props.create(Master.class, nrOfWorkers, nrOfMessages, nrOfElements, listener), "master");

    master.tell(new Calculate(), ActorRef.noSender());
  }
}




