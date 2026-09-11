package dev.emptyhans.nicknamegenerator;

import android.app.*;import android.os.*;import android.graphics.Color;import android.graphics.Typeface;import android.content.*;import android.view.*;import android.widget.*;import java.util.*;

public class MainActivity extends Activity {
  final Random r=new Random(); LinearLayout options,results; Spinner mode; final List<Row> rows=new ArrayList<>();
  static final int IN_NAME=0,IN_HEAD=1,IN_MIDDLE=2,IN_TAIL=3;
  static class Row { String label; String[] words; CheckBox use; Spinner pos; Row(String l,String[] w){label=l;words=w;} }
  final String[] family={"星宮","月城","白石","雨宮","神代","天羽","黒川","桜庭","水瀬","柊木","朝霧","御影","風間","雪村","一ノ瀬","七海"};
  final String[] given={"ルナ","ミコト","レイ","シオン","カナデ","アオイ","ヒカリ","ユラ","ノア","スイ","トワ","リリカ","ナギ","セナ"};
  final String[] title={"深夜の司書","電脳の吟遊詩人","無重力の料理人","黄昏の観測者","路地裏の錬金術師","静寂の収集家","月面喫茶の店主","異界の編集者"};
  final String[] food={"焦がし月光のカルボナーラ","星屑仕立てのオムライス","深夜喫茶の固めプリン","黄昏果実のタルト","白夜仕込みのビーフシチュー","雨音香るスコーン"};
  final String[] penFamily={"余白","常盤","久遠","白群","薄明","灯里","紙屋","綴木","青磁","冬青"};
  final String[] penGiven={"栞","朔","澄","綴","環","凪","透","灯","律","墨"};
  final String[] element={"月","星","雨","白","黒","夢","夜","空","音","光","花","雪","風","海","天","影"};

  @Override public void onCreate(Bundle b){super.onCreate(b); build();}
  TextView text(String s,int sp,boolean bold){TextView v=new TextView(this);v.setText(s);v.setTextSize(sp);v.setTextColor(Color.rgb(24,28,32));v.setTypeface(null,bold?Typeface.BOLD:Typeface.NORMAL);v.setPadding(0,8,0,8);return v;}
  void build(){ScrollView scroll=new ScrollView(this); LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(36,40,36,60);root.setBackgroundColor(Color.rgb(244,242,237));scroll.addView(root);
    TextView h=text("NAME LAB / 10",28,true);root.addView(h);root.addView(text("偶然性から、使える名前を拾う。",14,false));
    mode=new Spinner(this);String[] ms={"架空VTuber名","ペンネーム","二つ名・肩書き","架空の料理名","漢字シャッフル名"};ArrayAdapter<String>a=new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,ms);mode.setAdapter(a);root.addView(mode,new LinearLayout.LayoutParams(-1,64));
    root.addView(text("必ず含める要素 / 配置",16,true));options=new LinearLayout(this);options.setOrientation(LinearLayout.VERTICAL);root.addView(options);
    addRow("月・夜",new String[]{"月","夜","宵","ルナ"});addRow("自然",new String[]{"雨","風","雪","花","森","海"});addRow("幻想",new String[]{"夢","幻","異界","星屑","黄昏"});addRow("創作",new String[]{"綴","絵","本","詩","音"});
    Button gen=new Button(this);gen.setText("10個生成する");gen.setTextSize(18);gen.setOnClickListener(v->generate());root.addView(gen,new LinearLayout.LayoutParams(-1,72));
    results=new LinearLayout(this);results.setOrientation(LinearLayout.VERTICAL);root.addView(results);setContentView(scroll);generate(); }
  void addRow(String l,String[]w){Row row=new Row(l,w);LinearLayout line=new LinearLayout(this);line.setGravity(Gravity.CENTER_VERTICAL);row.use=new CheckBox(this);row.use.setText(l);row.pos=new Spinner(this);row.pos.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"名前内","前に置く","中央","後ろに置く"}));line.addView(row.use,new LinearLayout.LayoutParams(0,60,1));line.addView(row.pos,new LinearLayout.LayoutParams(0,60,1));options.addView(line);rows.add(row);}
  String pick(String[]x){return x[r.nextInt(x.length)];}
  String shuffle(){String a=pick(element)+pick(element),b=pick(element)+pick(element);return a+" "+b;}
  String base(){switch(mode.getSelectedItemPosition()){case 1:return pick(penFamily)+" "+pick(penGiven);case 2:return pick(title);case 3:return pick(food);case 4:return shuffle();default:return pick(family)+" "+pick(given);}}
  String make(){String main=base(),head="",mid="",tail="";for(Row x:rows)if(x.use.isChecked()){String w=pick(x.words);switch(x.pos.getSelectedItemPosition()){case IN_HEAD:head+=w+"・";break;case IN_MIDDLE:mid+="《"+w+"》";break;case IN_TAIL:tail+="・"+w;break;default:main=insert(main,w);}}return head+main+mid+tail;}
  String insert(String s,String w){int p=s.indexOf(' ');return p<0?w+s:s.substring(0,p)+w+s.substring(p);}
  void generate(){results.removeAllViews();for(int i=0;i<10;i++){final String value=make();TextView v=text(String.format(Locale.JAPAN,"%02d  %s",i+1,value),19,true);v.setPadding(12,18,12,18);v.setOnLongClickListener(x->{((android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("generated name",value));Toast.makeText(this,"コピーしました",Toast.LENGTH_SHORT).show();return true;});results.addView(v);}}
}
