package dev.asaul.litematicainventories;
import java.util.*;
public final class LogicTests {
 private static int checks;
 private static void check(boolean b,String name){checks++;if(!b)throw new AssertionError(name);}
 private static ComparisonEngine.Amount<String> a(String key,long count){return new ComparisonEngine.Amount<>(key,count);}
 public static void main(String[] args){
  var empty=a(null,0);var diamond=a("diamond",32);
  check(ComparisonEngine.strict(diamond,empty)==ComparisonEngine.Status.MISSING,"missing");
  check(ComparisonEngine.strict(empty,diamond)==ComparisonEngine.Status.EXTRA,"extra");
  check(ComparisonEngine.strict(diamond,a("diamond",31))==ComparisonEngine.Status.UNDER,"under");
  check(ComparisonEngine.strict(diamond,a("diamond",33))==ComparisonEngine.Status.OVER,"over");
  check(ComparisonEngine.strict(diamond,a("diamond",32))==ComparisonEngine.Status.MATCH,"match");
  check(ComparisonEngine.strict(empty,empty)==ComparisonEngine.Status.MATCH,"empty");
  check(ComparisonEngine.strict(diamond,a("diamond+name",32))==ComparisonEngine.Status.WRONG,"components");
  check(ComparisonEngine.strict(diamond,null)==ComparisonEngine.Status.UNKNOWN,"unknown");
  var expected=new ArrayList<>(Collections.nCopies(54,empty));expected.set(0,diamond);
  var actual=new ArrayList<>(Collections.nCopies(54,empty));actual.set(5,diamond);
  var t=ComparisonEngine.aggregate(expected,actual).get("diamond");
  check(t.missing()==0&&t.extra()==0,"54-slot reorder");
  actual.set(5,a("diamond",12));actual.set(53,a("diamond",20));
  check(ComparisonEngine.aggregate(expected,actual).get("diamond").missing()==0,"split stacks");
  actual.set(53,a("diamond",10));check(ComparisonEngine.aggregate(expected,actual).get("diamond").missing()==10,"partial shortage");
  actual.set(53,a("diamond",30));check(ComparisonEngine.aggregate(expected,actual).get("diamond").extra()==10,"excess");
  var c1=ComparisonEngine.aggregate(List.of(a("diamond",32)),List.of(empty)).get("diamond");
  var c2=ComparisonEngine.aggregate(List.of(empty),List.of(a("diamond",32))).get("diamond");
  check(c1.missing()+c2.missing()==32&&c1.extra()+c2.extra()==32,"containers do not compensate");
  var variants=ComparisonEngine.aggregate(List.of(a("potion+healing",2)),List.of(a("potion+poison",2)));
  check(variants.get("potion+healing").missing()==2&&variants.get("potion+poison").extra()==2,"component variants");
  Random rng=new Random(823713);
  for(int run=0;run<1000;run++){
   List<ComparisonEngine.Amount<String>> e=new ArrayList<>(),v=new ArrayList<>();
   for(int i=0;i<54;i++){e.add(a("item"+rng.nextInt(8),rng.nextInt(65)));v.add(a("item"+rng.nextInt(8),rng.nextInt(65)));}
   var totals=ComparisonEngine.aggregate(e,v);
   for(var k:totals.values())check(k.expected()-k.actual()==k.missing()-k.extra()&&k.missing()*k.extra()==0,"conservation");
   Collections.shuffle(e,rng);Collections.shuffle(v,rng);check(totals.equals(ComparisonEngine.aggregate(e,v)),"order invariant");
  }
  System.out.println("LOGIC PASS: "+checks+" assertions");
 }
}