import com.xuan.fitai.nutrition.*;
import com.xuan.fitai.ai.GemmaFoodAnalysis;
import java.lang.reflect.*;
import java.util.*;
public class ReviewProbe {
 static Method parser;
 static String row(String name,String serving) { return "<td class=\"borderBottom\"><a class=\"prominent\" href=\"/food\">"+name+"</a><div class=\"smallText greyText greyLink\">每"+serving+" - 卡路里: 90千卡 | 脂肪: 7克 | 碳水物: 0.6克 | 蛋白质: 6克</div></td>"; }
 @SuppressWarnings("unchecked") static List<NutritionFood> parse(String query,String name,String serving) throws Exception { return (List<NutritionFood>)parser.invoke(WebNutritionScraper.INSTANCE, query, row(name,serving)); }
 public static void main(String[] args) throws Exception {
  parser=Arrays.stream(WebNutritionScraper.class.getDeclaredMethods()).filter(m->m.getName().startsWith("parseHtml")).findFirst().get();
  var unknown=parse("雞蛋","鸡蛋","1个"); var known=parse("雞蛋","鸡蛋","1个(50克)");
  System.out.println("unknown serving -> per100g: "+unknown.get(0).getCalories());
  System.out.println("known 50g serving -> per100g: "+known.get(0).getCalories());
  var a = new GemmaFoodAnalysis(0f,0f,0f,0f,true,"","",null,"WEB-1");
  System.out.println("unknown serving + 200g => "+NutritionPortionCalculator.INSTANCE.apply(a,new NutritionReference(1L,false,unknown,null),"200g").getCalories()+" kcal");
  System.out.println("known 50g serving + 200g => "+NutritionPortionCalculator.INSTANCE.apply(a,new NutritionReference(1L,false,known,null),"200g").getCalories()+" kcal");
  System.out.println("牛肉麵 matches: "+parse("牛肉麵","牛肉酱","100克").stream().map(NutritionFood::getName).toList());
  for(String q:List.of("低脂鮮奶","無糖鮮奶茶","麥香魚","滷海帶")) {
   var found=NutritionMatcher.INSTANCE.find(q,TaiwaneseCommonFoods.INSTANCE.getALL());
   System.out.println(q+" -> "+found.stream().map(f->f.getName()+" ["+f.getDescription()+"]").toList());
  }
 }
}
