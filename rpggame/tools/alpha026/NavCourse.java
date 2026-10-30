package rpg;
import astratto.bridge.*;import astratto.game.*;import java.util.*;
import static astratto.bridge.AstrattoNative.*;
/** Native reproducible routes: a pocket, a crowded one-cell door, and a changing wall. */
public class NavCourse {
 static final int F=4;
 static class Course implements GameModule {
  final String kind;final int count;long[] agents,bodies;float[] startX,startZ,goalX,goalZ,lastX,lastZ;double[] distance,arrive;int frame,stuck,arrivedEvents;long pathQueries;int pathExpansions;double optimal;
  Course(String kind){this.kind=kind;count=kind.equals("door")?20:1;agents=new long[count];bodies=new long[count];startX=new float[count];startZ=new float[count];goalX=new float[count];goalZ=new float[count];lastX=new float[count];lastZ=new float[count];distance=new double[count];arrive=new double[count];Arrays.fill(arrive,-1);}
  public void init(GameContext ctx){var c=ctx.commands();long policy=ctx.reserve(KIND_POLICY);c.policyCreate(policy,List.of(new CommandBuffer.Action(1,ACT_MOVE_TO_GOAL).base(1).speed(3.5f)),0.05f,0.1f);
   if(kind.equals("pocket")){c.fill(18,F,25,28,F+3,25,1);c.fill(18,F,39,28,F+3,39,1);c.fill(28,F,25,28,F+3,39,1);}
   if(kind.equals("door")){c.fill(32,F,1,32,F+3,31,1);c.fill(32,F,33,32,F+3,62,1);}
   for(int i=0;i<count;i++){boolean east=kind.equals("door")&&i>=10;startX[i]=kind.equals("pocket")?25.5f:east?47.5f+(i%5)*0.8f:12.5f+(i%5)*0.8f;startZ[i]=kind.equals("door")?29.8f+(i/5%2)*1.1f:32.5f;goalX[i]=east?12.5f:50.5f;goalZ[i]=kind.equals("door")?29.5f+(i%5)*0.9f:32.5f;lastX[i]=startX[i];lastZ[i]=startZ[i];agents[i]=ctx.reserve(KIND_AGENT);bodies[i]=ctx.reserve(KIND_MOVER);c.moverCreate(bodies[i],startX[i],F,startZ[i],0.28f,1.8f,0.6f,MOVER_AGENT,1.2f);c.agentCreate(agents[i],policy,bodies[i],startX[i],F,startZ[i],1.6f,1,1,0,1,0,100+i);c.agentGoal(agents[i],goalX[i],F,goalZ[i]);}
   c.pathRequest(1,PATH_GOAL_Y,startX[0],F,startZ[0],goalX[0],F,goalZ[0],5000);
  }
  public void update(GameContext ctx,float dt){frame++;if(kind.equals("wall")&&frame==120)ctx.commands().fill(28,F,15,28,F+3,39,1);}
  public void path(int id,int[] cells,int expansions){pathExpansions=expansions;if(cells!=null){float x=startX[0],z=startZ[0];for(int i=0;i<cells.length;i+=3){optimal+=Math.hypot(cells[i]+.5-x,cells[i+2]+.5-z);x=cells[i]+.5f;z=cells[i+2]+.5f;}optimal/=3.5;}}
  public void agentStuck(long agent){stuck++;}public void agentArrived(long agent,long target){arrivedEvents++;}
  public void afterFrame(GameContext ctx){for(int i=0;i<count;i++){var a=ctx.agents().agent(agents[i]);if(a==null)continue;distance[i]+=Math.hypot(a.x-lastX[i],a.z-lastZ[i]);lastX[i]=a.x;lastZ[i]=a.z;if(arrive[i]<0&&Math.hypot(a.x-goalX[i],a.z-goalZ[i])<0.65)arrive[i]=frame/60.0;}}
  void report(Game game){var stats=game.engine().stats();int reached=0;double seconds=0,length=0;long partials=0,caps=0,repaths=0,yields=0;for(int i=0;i<count;i++){if(arrive[i]>=0){reached++;seconds=Math.max(seconds,arrive[i]);}length+=distance[i];var a=game.engine().agents().agent(agents[i]);for(String name:new String[]{"partialPaths","capHits","repaths","yields"})try{long value=((Number)a.getClass().getField(name).get(a)).longValue();switch(name){case "partialPaths"->partials+=value;case "capHits"->caps+=value;case "repaths"->repaths+=value;case "yields"->yields+=value;}}catch(ReflectiveOperationException ignored){partials=caps=repaths=yields=-1;}}
   double lower=0;for(int i=0;i<count;i++)lower=Math.max(lower,geometric(kind,startX[i],startZ[i],goalX[i],goalZ[i]));
   System.out.printf(Locale.ROOT,"%s pointAgentLowerBound=%.2fs arrivalRatio=%.3f%n",kind,lower,seconds/lower);
   System.out.printf(Locale.ROOT,"%s reached=%d/%d stuck=%d events=%d requests=%d rawExpansions=%d partials=%d capHits=%d repaths=%d yields=%d travel=%.2fm lastArrival=%.2fs gridReference=%.2fs ratioToGrid=%.2f%n",kind,reached,count,stuck,arrivedEvents,stats.pathRequests,pathExpansions,partials,caps,repaths,yields,length,seconds,optimal,optimal>0&&count==1?seconds/optimal:0);
  }
 }
 /** Exact shortest point route around final axis-aligned obstacles; ignores acceleration, radius and other agents.
  * The changing-wall bound assumes advance knowledge, so it is deliberately an optimistic oracle. */
 static double geometric(String kind,float sx,float sz,float tx,float tz){
  double[][] obstacles=switch(kind){case "pocket"->new double[][]{{18,25,29,26},{18,39,29,40},{28,25,29,40}};case "door"->new double[][]{{32,1,33,32},{32,33,33,63}};default->new double[][]{{28,15,29,40}};};
  var nodes=new ArrayList<double[]>();nodes.add(new double[]{sx,sz});nodes.add(new double[]{tx,tz});
  for(var box:obstacles)for(double x:new double[]{box[0],box[2]})for(double z:new double[]{box[1],box[3]})nodes.add(new double[]{x,z});
  double[] distances=new double[nodes.size()];Arrays.fill(distances,Double.POSITIVE_INFINITY);distances[0]=0;boolean[] seen=new boolean[nodes.size()];
  for(int step=0;step<nodes.size();step++){
   int at=-1;for(int i=0;i<nodes.size();i++)if(!seen[i]&&(at<0||distances[i]<distances[at]))at=i;
   if(at<0||!Double.isFinite(distances[at]))break;if(at==1)return distances[at]/3.5;seen[at]=true;
   var a=nodes.get(at);
   for(int i=0;i<nodes.size();i++)if(!seen[i]){var b=nodes.get(i);boolean clear=true;
    for(var box:obstacles)if(new java.awt.geom.Rectangle2D.Double(box[0]+.00001,box[1]+.00001,box[2]-box[0]-.00002,box[3]-box[1]-.00002).intersectsLine(a[0],a[1],b[0],b[1])){clear=false;break;}
    if(clear)distances[i]=Math.min(distances[i],distances[at]+Math.hypot(a[0]-b[0],a[1]-b[1]));
   }
  }return Double.POSITIVE_INFINITY;
 }
 public static void main(String[]args){for(String kind:new String[]{"pocket","door","wall"}){var cfg=new NativeEngine.Config();cfg.audioDevice=false;cfg.deterministic=true;cfg.workerThreads=2;try(var game=Game.create(cfg)){var defs=new NativeEngine.BlockDef[]{new NativeEngine.BlockDef(0),new NativeEngine.BlockDef(BLOCK_SOLID|BLOCK_OPAQUE)};game.engine().blockTable(defs);long w=game.engine().createWorld(64,24,64,1,null,null,0);var c=new CommandBuffer();c.fill(0,0,0,63,F-1,63,1|SET_RAW);game.engine().edit(w,c);c.free();game.activate(w);var course=game.add(new Course(kind));for(int frame=0;frame<60*45;frame++)game.tick(1f/60);course.report(game);}}}
}
