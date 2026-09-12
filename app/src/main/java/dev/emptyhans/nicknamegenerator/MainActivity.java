package dev.emptyhans.nicknamegenerator;

import android.app.*;
import android.os.*;
import android.graphics.*;
import android.content.*;
import android.net.*;
import android.provider.*;
import android.database.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.nio.*;
import java.nio.charset.*;
import java.util.*;
import java.util.regex.*;

public class MainActivity extends Activity {
    static final int OPEN_CSV=41, SAVE_CSV=42;
    final Random rnd=new Random();
    final Part[] parts=new Part[6];
    final LinkedHashMap<String,Data> presets=new LinkedHashMap<>();
    final ArrayList<String> history=new ArrayList<>(), favorites=new ArrayList<>();
    Spinner dbSpinner, patternSpinner, creativitySpinner, countSpinner, maxOutputSpinner;
    EditText patternEdit;
    CheckBox patternFixed, naturalPriority, avoidDup, streamMode;
    TextView status;
    LinearLayout results;
    Data current, imported;
    SharedPreferences prefs;

    static class Data {
        String name;
        final LinkedHashMap<String,List<String>> cols=new LinkedHashMap<>();
        final ArrayList<String> patterns=new ArrayList<>();
        Data(String name){this.name=name;for(int i=1;i<=6;i++)cols.put("P"+i,new ArrayList<>());} 
        List<String> col(String k){if(!cols.containsKey(k))cols.put(k,new ArrayList<>());return cols.get(k);} 
    }
    static class Part {
        String key; Spinner candidate,pos,min,max; EditText direct;
        CheckBox fixed,required,kanji,natural;
        Part(String k){key=k;}
        String value(){String d=direct.getText().toString().trim();if(!d.isEmpty())return d;Object o=candidate.getSelectedItem();return o==null?"":o.toString().trim();}
    }
    static class Cand {String[] v=new String[6];String pat,result;double score;}

    @Override public void onCreate(Bundle b){super.onCreate(b);prefs=getSharedPreferences("name_lab",MODE_PRIVATE);buildPresets();loadSavedLists();loadImported();buildUi();}

    void buildUi(){
        ScrollView sc=new ScrollView(this); LinearLayout root=vbox(); root.setPadding(dp(16),dp(18),dp(16),dp(40)); root.setBackgroundColor(Color.rgb(246,245,241)); sc.addView(root);
        root.addView(txt("NAME LAB / FULL",28,true)); root.addView(txt("Windows版の主要オプションをAndroidへ",13,false));

        root.addView(section("DATABASE"));
        dbSpinner=spinner(new ArrayList<>(presets.keySet())); root.addView(dbSpinner,match(50));
        LinearLayout dbBtns=hbox(); Button load=button("CSV読込"), save=button("CSV書出"); dbBtns.addView(load,weight());dbBtns.addView(save,weight());root.addView(dbBtns);
        status=txt("",12,false);root.addView(status);

        root.addView(section("PATTERN"));
        patternSpinner=spinner(Arrays.asList("[P1][P2]"));root.addView(patternSpinner,match(48));
        patternEdit=edit("直接入力 例：[P2]の[P1]");root.addView(patternEdit,match(46));
        patternFixed=check("パターン固定");root.addView(patternFixed);

        root.addView(section("GLOBAL"));
        LinearLayout g1=hbox(); naturalPriority=check("自然さ優先");avoidDup=check("重複回避");streamMode=check("配信向け");naturalPriority.setChecked(true);avoidDup.setChecked(true);streamMode.setChecked(true);g1.addView(naturalPriority,weight());g1.addView(avoidDup,weight());g1.addView(streamMode,weight());root.addView(g1);
        LinearLayout g2=hbox(); creativitySpinner=spinner(Arrays.asList("自然重視","バランス","変化重視"));creativitySpinner.setSelection(1);countSpinner=spinner(Arrays.asList("1","5","10","20"));countSpinner.setSelection(2);maxOutputSpinner=spinner(Arrays.asList("6","8","10","12","14","16","18","20","24","30"));maxOutputSpinner.setSelection(4);g2.addView(wrap("創造性",creativitySpinner),weight());g2.addView(wrap("生成数",countSpinner),weight());g2.addView(wrap("最大文字",maxOutputSpinner),weight());root.addView(g2);

        root.addView(section("P1 - P6"));
        for(int i=0;i<6;i++){parts[i]=makePart("P"+(i+1));root.addView(partCard(parts[i]));}
        LinearLayout q=hbox();Button allReq=button("全必須"),clearReq=button("必須解除"),clearFix=button("固定解除");q.addView(allReq,weight());q.addView(clearReq,weight());q.addView(clearFix,weight());root.addView(q);

        LinearLayout gen=hbox();Button make=button("生成"),random=button("すべてランダム");make.setTextSize(18);gen.addView(make,weight());gen.addView(random,weight());root.addView(gen);
        root.addView(section("RESULT"));results=vbox();root.addView(results);
        LinearLayout util=hbox();Button copy=button("全コピー"),share=button("共有"),hist=button("履歴"),fav=button("お気に入り");util.addView(copy,weight());util.addView(share,weight());util.addView(hist,weight());util.addView(fav,weight());root.addView(util);
        setContentView(sc);

        load.setOnClickListener(v->openCsv());save.setOnClickListener(v->saveCsv());make.setOnClickListener(v->generate(false));random.setOnClickListener(v->generate(true));copy.setOnClickListener(v->copyText(resultText()));share.setOnClickListener(v->shareText(resultText()));hist.setOnClickListener(v->showList("履歴",history,false));fav.setOnClickListener(v->showList("お気に入り",favorites,true));
        allReq.setOnClickListener(v->{for(Part p:parts)p.required.setChecked(true);});clearReq.setOnClickListener(v->{for(Part p:parts)p.required.setChecked(false);});clearFix.setOnClickListener(v->{for(Part p:parts)p.fixed.setChecked(false);patternFixed.setChecked(false);});
        dbSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){public void onItemSelected(android.widget.AdapterView<?> a,View v,int p,long id){current=presets.get(a.getItemAtPosition(p).toString());refresh();}public void onNothingSelected(android.widget.AdapterView<?> a){}});
        current=presets.values().iterator().next();refresh();generate(false);
    }

    Part makePart(String key){Part p=new Part(key);p.candidate=spinner(Arrays.asList(""));p.direct=edit("直接入力（空欄なら候補）");p.fixed=check("固定");p.required=check("必須");p.kanji=check("漢字混成");p.natural=check("自然造語");p.pos=spinner(Arrays.asList("自動","先頭","P1の後","P2の後","P3の後","P4の後","P5の後","P6の後","末尾"));p.min=spinner(Arrays.asList("1","2","3","4","5","6"));p.min.setSelection(1);p.max=spinner(Arrays.asList("2","3","4","5","6","7","8"));p.max.setSelection(2);p.kanji.setOnCheckedChangeListener((b,c)->{if(c)p.natural.setChecked(false);});p.natural.setOnCheckedChangeListener((b,c)->{if(c)p.kanji.setChecked(false);});return p;}
    View partCard(Part p){LinearLayout c=vbox();c.setPadding(dp(10),dp(8),dp(10),dp(8));c.setBackgroundColor(Color.WHITE);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(0,dp(4),0,dp(7));c.setLayoutParams(lp);c.addView(txt(p.key,17,true));c.addView(p.candidate,match(46));c.addView(p.direct,match(44));LinearLayout ck=hbox();ck.addView(p.fixed,weight());ck.addView(p.required,weight());ck.addView(p.kanji,weight());ck.addView(p.natural,weight());c.addView(ck);LinearLayout st=hbox();st.addView(wrap("挿入位置",p.pos),new LinearLayout.LayoutParams(0,-2,2));st.addView(wrap("最小",p.min),weight());st.addView(wrap("最大",p.max),weight());c.addView(st);return c;}

    void refresh(){if(current==null)return;for(int i=0;i<6;i++){List<String>s=current.col("P"+(i+1));if(s.isEmpty())s=Arrays.asList("");parts[i].candidate.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,s));}List<String>p=current.patterns.isEmpty()?Arrays.asList("[P1][P2]"):current.patterns;patternSpinner.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,p));status.setText("DB："+current.name+" / "+wordCount(current)+"語 / "+p.size()+"パターン");}
    int wordCount(Data d){int n=0;for(List<String>x:d.cols.values())n+=x.size();return n;}

    void generate(boolean randomAll){int count=Integer.parseInt(countSpinner.getSelectedItem().toString());LinkedHashSet<String>batch=new LinkedHashSet<>();ArrayList<Cand>out=new ArrayList<>();for(int n=0;n<count;n++){Cand best=null;int tries=naturalPriority.isChecked()?32:5;for(int t=0;t<tries;t++){Cand c=buildCandidate(randomAll||n>0,batch);if(best==null||c.score>best.score)best=c;}if(best!=null){batch.add(best.result);out.add(best);addHistory(best.result);}}showResults(out);status.setText("DB："+current.name+" / "+out.size()+"件生成"+(avoidDup.isChecked()?" / 重複回避":"")+(naturalPriority.isChecked()?" / 自然さ評価":""));}

    Cand buildCandidate(boolean randomize,Set<String>batch){Cand c=new Cand();HashSet<String>used=new HashSet<>();for(int i=0;i<6;i++){Part p=parts[i];String cur=p.value();int min=num(p.min,2),max=num(p.max,4);if(max<min)max=min;String val;if(p.fixed.isChecked()&&!cur.isEmpty())val=cur;else if(p.kanji.isChecked())val=kanjiMix(p.key,min,max);else if(p.natural.isChecked())val=naturalWord(p.key,min,max);else if(!randomize&&!cur.isEmpty())val=cur;else val=pick(current.col(p.key));if(p.required.isChecked()&&val.isEmpty())val=pick(current.col(p.key));if(avoidDup.isChecked()&&!val.isEmpty()&&used.contains(val)&&!p.fixed.isChecked()){for(int r=0;r<10;r++){String a=p.kanji.isChecked()?kanjiMix(p.key,min,max):p.natural.isChecked()?naturalWord(p.key,min,max):pick(current.col(p.key));if(!used.contains(a)&&!a.isEmpty()){val=a;break;}}}c.v[i]=val;if(!val.isEmpty())used.add(val);}String typed=patternEdit.getText().toString().trim();String selected=patternSpinner.getSelectedItem()==null?"[P1][P2]":patternSpinner.getSelectedItem().toString();if(patternFixed.isChecked())c.pat=typed.isEmpty()?selected:typed;else if(!randomize)c.pat=typed.isEmpty()?selected:typed;else c.pat=current.patterns.isEmpty()?"[P1][P2]":pick(current.patterns);c.result=clean(renderRequired(c.pat,c.v));c.score=score(c,batch);return c;}

    String renderRequired(String pat,String[]v){String original=(pat==null||pat.trim().isEmpty())?"[P1][P2]":pat.trim(),work=original;for(int i=0;i<6;i++)if(parts[i].required.isChecked()&&parts[i].pos.getSelectedItemPosition()!=0)work=work.replace("[P"+(i+1)+"]","");String r=work;for(int i=0;i<6;i++)r=r.replace("[P"+(i+1)+"]",safe(v[i]));for(int i=0;i<6;i++){Part p=parts[i];if(!p.required.isChecked())continue;String val=safe(v[i]);if(val.isEmpty())continue;int pos=p.pos.getSelectedItemPosition();if(pos==0){if(!original.contains("[P"+(i+1)+"]")&&!r.contains(val)){int a=anchor(original,i);r=a>=0&&!safe(v[a]).isEmpty()?after(r,v[a],val):r+val;}}else if(pos==1)r=val+r;else if(pos==8)r=r+val;else{int a=pos-2;r=(a>=0&&a<6&&!safe(v[a]).isEmpty())?after(r,v[a],val):r+val;}if(!r.contains(val))r+=val;}return r;}
    int anchor(String pat,int target){for(int d=1;d<6;d++){int lo=target-d;if(lo>=0&&pat.contains("[P"+(lo+1)+"]"))return lo;int hi=target+d;if(hi<6&&pat.contains("[P"+(hi+1)+"]"))return hi;}return-1;}
    String after(String s,String a,String v){int i=s.indexOf(a);if(i<0)return s+v;int e=i+a.length();return s.substring(0,e)+v+s.substring(e);}

    double score(Cand c,Set<String>batch){String s=c.result;double z=100;int max=Integer.parseInt(maxOutputSpinner.getSelectedItem().toString());if(s.isEmpty())return-9999;if(s.length()>max)z-=(s.length()-max)*35;else z+=18;if(streamMode.isChecked()){if(s.matches(".*[A-Za-z0-9０-９].*"))z-=600;if(s.length()>max)z-=300;if(s.length()<=14)z+=20;}if(batch.contains(s))z-=avoidDup.isChecked()?5000:80;if(repeatRun(s))z-=80;if(s.contains("・・")||s.contains("のの")||s.contains("  "))z-=50;for(int i=0;i<6;i++){if(parts[i].required.isChecked()&&!safe(c.v[i]).isEmpty()&&!s.contains(c.v[i]))z-=5000;if(parts[i].kanji.isChecked())z+=kanjiScore(c.v[i]);}return z;}

    String kanjiMix(String key,int min,int max){List<String>src=current.col(key);ArrayList<Character>first=new ArrayList<>(),last=new ArrayList<>(),all=new ArrayList<>();HashSet<String>existing=new HashSet<>();for(String raw:src){String k=onlyKanji(raw);if(k.isEmpty())continue;existing.add(k);first.add(k.charAt(0));last.add(k.charAt(k.length()-1));for(int i=0;i<k.length();i++)all.add(k.charAt(i));}if(all.size()<2)return naturalWord(key,min,max);min=Math.max(2,min);max=Math.max(min,Math.min(streamMode.isChecked()?3:4,max));String best="";double bs=-9999;int cr=creativitySpinner.getSelectedItemPosition();double structured=cr==0?.9:cr==2?.45:.7;for(int t=0;t<100;t++){int len=min+rnd.nextInt(max-min+1);StringBuilder b=new StringBuilder();HashSet<Character>u=new HashSet<>();for(int i=0;i<len;i++){List<Character>pool=(i==0&&rnd.nextDouble()<structured)?first:(i==len-1&&rnd.nextDouble()<structured)?last:all;if(pool.isEmpty())pool=all;char ch=pool.get(rnd.nextInt(pool.size()));for(int rr=0;rr<16&&u.contains(ch);rr++)ch=pool.get(rnd.nextInt(pool.size()));b.append(ch);u.add(ch);}String x=b.toString();if(existing.contains(x))continue;double sc=kanjiScore(x);if(sc>bs){bs=sc;best=x;}}return best.isEmpty()?pick(src):best;}
    double kanjiScore(String s){if(s==null||s.isEmpty())return-100;double z=(s.length()==2||s.length()==3)?35:15;HashSet<Character>seen=new HashSet<>();for(int i=0;i<s.length();i++){char c=s.charAt(i);if(!isKanji(c))z-=45;if(seen.contains(c))z-=30;seen.add(c);}return z;}

    String naturalWord(String key,int min,int max){List<String>src=current.col(key);if(src.isEmpty())return"";min=Math.max(1,min);max=Math.max(min,Math.min(10,max));ArrayList<Character>starts=new ArrayList<>();HashMap<Character,ArrayList<Character>>next=new HashMap<>();HashSet<String>existing=new HashSet<>(src);for(String w:src){if(w==null||w.length()<2)continue;starts.add(w.charAt(0));for(int i=0;i<w.length()-1;i++){char a=w.charAt(i),b=w.charAt(i+1);if(!next.containsKey(a))next.put(a,new ArrayList<>());next.get(a).add(b);}}if(starts.isEmpty())return pick(src);String best="";double bs=-9999;int cr=creativitySpinner.getSelectedItemPosition();for(int t=0;t<100;t++){int len=min+rnd.nextInt(max-min+1);StringBuilder b=new StringBuilder();b.append(starts.get(rnd.nextInt(starts.size())));while(b.length()<len){char last=b.charAt(b.length()-1);ArrayList<Character>n=next.get(last);if(n==null||n.isEmpty()||(cr==2&&rnd.nextDouble()<.28)){String seed=pick(src);if(seed.isEmpty())break;b.append(seed.charAt(rnd.nextInt(seed.length())));}else b.append(n.get(rnd.nextInt(n.size())));}String x=b.toString();if(existing.contains(x))continue;double sc=30-Math.abs(x.length()-(min+max)/2.0)*4+(repeatRun(x)?-20:15);if(sc>bs){bs=sc;best=x;}}return best.isEmpty()?pick(src):best;}

    void showResults(List<Cand>list){results.removeAllViews();for(int i=0;i<list.size();i++){final String value=list.get(i).result;LinearLayout row=hbox();TextView t=txt(String.format(Locale.JAPAN,"%02d｜%s",i+1,value),18,true);t.setPadding(dp(8),dp(10),dp(8),dp(10));Button star=button(favorites.contains(value)?"★":"☆");row.addView(t,new LinearLayout.LayoutParams(0,-2,1));row.addView(star,new LinearLayout.LayoutParams(dp(54),dp(48)));results.addView(row);t.setOnClickListener(v->copyText(value));t.setOnLongClickListener(v->{copyText(value);return true;});star.setOnClickListener(v->{if(favorites.contains(value))favorites.remove(value);else favorites.add(0,value);saveLists();star.setText(favorites.contains(value)?"★":"☆");});}}
    void addHistory(String s){history.remove(s);history.add(0,s);while(history.size()>100)history.remove(history.size()-1);saveLists();}
    void showList(String title,List<String>list,boolean share){ScrollView sc=new ScrollView(this);LinearLayout box=vbox();box.setPadding(dp(12),dp(8),dp(12),dp(8));sc.addView(box);if(list.isEmpty())box.addView(txt("まだありません",15,false));for(String s:list){TextView t=txt(s,17,false);t.setPadding(dp(8),dp(9),dp(8),dp(9));t.setOnClickListener(v->copyText(s));box.addView(t);}AlertDialog.Builder b=new AlertDialog.Builder(this).setTitle(title).setView(sc).setNegativeButton("閉じる",null).setNeutralButton("全消去",(d,w)->{list.clear();saveLists();});if(share)b.setPositiveButton("共有",(d,w)->shareText(join(list)));b.show();}

    void openCsv(){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("*/*");startActivityForResult(i,OPEN_CSV);}
    void saveCsv(){Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("text/csv");i.putExtra(Intent.EXTRA_TITLE,"nickname_database.csv");startActivityForResult(i,SAVE_CSV);}
    @Override protected void onActivityResult(int q,int result,Intent data){super.onActivityResult(q,result,data);if(result!=RESULT_OK||data==null||data.getData()==null)return;try{Uri u=data.getData();if(q==OPEN_CSV){String body=decode(read(getContentResolver().openInputStream(u)));imported=parse(body);imported.name=fileName(u);saveImported(body,imported.name);presets.put("インポートCSV",imported);resetDb("インポートCSV");Toast.makeText(this,"CSVを読み込みました",0).show();}else if(q==SAVE_CSV){OutputStream out=getContentResolver().openOutputStream(u);if(out!=null){out.write(toCsv(current).getBytes(StandardCharsets.UTF_8));out.close();Toast.makeText(this,"CSVを書き出しました",0).show();}}}catch(Exception e){Toast.makeText(this,"処理エラー："+e.getMessage(),1).show();}}
    void resetDb(String select){ArrayList<String>names=new ArrayList<>(presets.keySet());dbSpinner.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,names));int n=names.indexOf(select);if(n>=0)dbSpinner.setSelection(n);}

    Data parse(String text)throws Exception{List<List<String>>tab=table(text);if(tab.isEmpty())throw new Exception("CSVが空です");List<String>h=tab.get(0);Data d=new Data("インポートCSV");d.cols.clear();for(String s:h){String k=cleanHead(s);if(!isPattern(k)&&!k.isEmpty())d.cols.put(k,new ArrayList<>());}for(int y=1;y<tab.size();y++){List<String>row=tab.get(y);for(int x=0;x<h.size();x++){String k=cleanHead(h.get(x)),v=x<row.size()?row.get(x).trim():"";if(v.isEmpty())continue;if(isPattern(k)){if(!d.patterns.contains(v))d.patterns.add(v);}else{List<String>col=d.cols.get(k);if(col!=null&&!col.contains(v))col.add(v);}}}for(int i=1;i<=6;i++)d.col("P"+i);if(d.patterns.isEmpty()){StringBuilder p=new StringBuilder();for(int i=1;i<=6;i++)if(!d.col("P"+i).isEmpty())p.append("[P").append(i).append("]");d.patterns.add(p.length()==0?"[P1][P2]":p.toString());}return d;}
    List<List<String>> table(String s){ArrayList<List<String>>all=new ArrayList<>();ArrayList<String>row=new ArrayList<>();StringBuilder c=new StringBuilder();boolean q=false;for(int i=0;i<s.length();i++){char z=s.charAt(i);if(z=='\"'){if(q&&i+1<s.length()&&s.charAt(i+1)=='\"'){c.append('\"');i++;}else q=!q;}else if(z==','&&!q){row.add(c.toString());c.setLength(0);}else if((z=='\n'||z=='\r')&&!q){if(z=='\r'&&i+1<s.length()&&s.charAt(i+1)=='\n')i++;row.add(c.toString());c.setLength(0);if(nonempty(row))all.add(row);row=new ArrayList<>();}else c.append(z);}row.add(c.toString());if(nonempty(row))all.add(row);return all;}
    String toCsv(Data d){StringBuilder b=new StringBuilder("P1,P2,P3,P4,P5,P6,patterns\r\n");int rows=d.patterns.size();for(int i=1;i<=6;i++)rows=Math.max(rows,d.col("P"+i).size());for(int r=0;r<rows;r++){for(int i=1;i<=6;i++){List<String>x=d.col("P"+i);b.append(cell(r<x.size()?x.get(r):"")).append(',');}b.append(cell(r<d.patterns.size()?d.patterns.get(r):"")).append("\r\n");}return b.toString();}
    String cell(String s){if(s==null)return"";return(s.contains(",")||s.contains("\"")||s.contains("\n"))?"\""+s.replace("\"","\"\"")+"\"":s;}

    void buildPresets(){Data g=new Data("一般単語");add(g,"P1","冷蔵庫","炊飯器","掃除機","洗濯機","扇風機","枕","時計","眼鏡");add(g,"P2","プリン","大根","こんにゃく","たまご","豆腐","納豆","猫","犬");add(g,"P3","半額の","ぬるい","眠たい","巨大な","静かな","忘れられた");add(g,"P4","台所","倉庫","駅前","屋上","地下室","公園");add(g,"P5","店長","見習い","係長","旅人","番人","熟睡中");add(g,"P6","太郎","次郎","三郎","丸","助","先生");pats(g,"[P1][P2]","[P3][P1]","[P4]の[P2]","[P1][P2][P6]");
        Data food=new Data("料理名");add(food,"P1","焦がしバター","柚子胡椒","白味噌","山椒","香味しょうゆ","炙りねぎ");add(food,"P2","炊き込みご飯","唐揚げ","うどん","ラーメン","パスタ","スープ","ハンバーグ");add(food,"P3","柚子香る","山椒香る","香ばしい","濃厚な","だし仕立ての");add(food,"P4","炭火","直火","じっくり","こんがり","ふんわり");add(food,"P5","半熟卵","大葉","海苔","舞茸","ねぎ");add(food,"P6","仕立て","風","添え","盛り");pats(food,"[P1]の[P2]","[P3][P2]","[P4]仕立ての[P2]","[P1]と[P5]の[P2]");
        Data vt=new Data("架空VTuber");add(vt,"P1","星宮","月城","白雪","黒羽","天音","朝霧","夕凪","水瀬","風見","桜庭","御影","神代");add(vt,"P2","ひかり","あかり","ゆめ","そら","つき","うた","りん","れん","こより","しずく","みこと","ゆら");add(vt,"P3","ふわふわ","のんびり","元気な","静かな","気まぐれ","不思議な","夢見る");add(vt,"P4","月","星","空","雲","雨","雪","風","海","深海","猫","狐","古書","魔法");add(vt,"P5","新人","見習い","案内人","旅人","配信者","歌い手","語り部","司書","星読み");add(vt,"P6","ちゃん","さん","くん","たん","りん","にゃ","姫","先生");pats(vt,"[P1][P2]","[P4]の[P2]","[P3][P2]","[P1][P2][P6]","[P4]の[P1][P2]");
        Data pen=new Data("ペンネーム");add(pen,"P1","青山","秋月","朝霧","天城","雨宮","有栖","香月","神代","如月","霧島","久世","桜庭","白瀬","月城","遠野","八雲");add(pen,"P2","あかり","あきら","あまね","いおり","かえで","ことは","しおり","しずく","つむぎ","なつめ","ほたる","みこと","ゆら","りつ");add(pen,"P3","静かな","淡い","深い","眠れる","漂う","夢見る","夜更けの","月夜の","余白の","記憶の","透明な");add(pen,"P4","月","星","夜","雨","雪","風","森","路地","灯り","本","古書","栞","手紙","インク","記憶","余白");add(pen,"P5","作家","小説家","詩人","書き手","物書き","語り手","編集者","旅人","夢想家","文章屋");add(pen,"P6","庵","堂","舎","房","亭","館","屋","文庫","書房","筆","録");pats(pen,"[P1][P2]","[P4]の[P2]","[P3][P2]","[P1][P2][P6]","[P4][P6]");
        Data k=new Data("漢字苗字・名前");add(k,"P1","星宮","月城","白雪","黒羽","天宮","雨宮","水瀬","風見","桜庭","花森","雪代","神楽","御影","鳴海","青葉","紅葉","秋月","光月","夢野","音羽","空野","深海","神代");add(k,"P2","光","凛","葵","奏","澪","玲","蓮","楓","椿","柚","月","星","空","海","雫","詩","音","夢","灯","霞","翠","碧","茜","藍","雪","花","桜","琴","響","栞");pats(k,"[P1][P2]");
        presets.put(g.name,g);presets.put(food.name,food);presets.put(vt.name,vt);presets.put(pen.name,pen);presets.put(k.name,k);
    }
    void add(Data d,String key,String...w){d.col(key).addAll(Arrays.asList(w));}void pats(Data d,String...p){d.patterns.addAll(Arrays.asList(p));}

    void saveImported(String body,String name)throws Exception{try(Writer w=new OutputStreamWriter(openFileOutput("imported.csv",0),StandardCharsets.UTF_8)){w.write(body);}try(Writer w=new OutputStreamWriter(openFileOutput("imported.name",0),StandardCharsets.UTF_8)){w.write(name);}}
    void loadImported(){try{String body=new String(read(openFileInput("imported.csv")),StandardCharsets.UTF_8);imported=parse(body);try{imported.name=new String(read(openFileInput("imported.name")),StandardCharsets.UTF_8).trim();}catch(Exception e){}presets.put("インポートCSV",imported);}catch(Exception e){}}
    void saveLists(){prefs.edit().putString("history",join(history)).putString("favorites",join(favorites)).apply();}
    void loadSavedLists(){split(prefs.getString("history",""),history);split(prefs.getString("favorites",""),favorites);}
    void split(String s,List<String>out){if(s==null)return;for(String x:s.split("\n"))if(!x.trim().isEmpty())out.add(x.trim());}

    String resultText(){StringBuilder b=new StringBuilder();for(int i=0;i<results.getChildCount();i++){View r=results.getChildAt(i);if(r instanceof LinearLayout){LinearLayout l=(LinearLayout)r;if(l.getChildCount()>0&&l.getChildAt(0)instanceof TextView){if(b.length()>0)b.append('\n');b.append(((TextView)l.getChildAt(0)).getText());}}}return b.toString();}
    void copyText(String s){if(s==null||s.isEmpty())return;((ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("NAME LAB",s));Toast.makeText(this,"コピーしました",0).show();}
    void shareText(String s){if(s==null||s.trim().isEmpty())return;Intent i=new Intent(Intent.ACTION_SEND);i.setType("text/plain");i.putExtra(Intent.EXTRA_TEXT,s);startActivity(Intent.createChooser(i,"共有"));}
    int num(Spinner s,int f){try{return Integer.parseInt(s.getSelectedItem().toString());}catch(Exception e){return f;}}
    String pick(List<String>a){return a==null||a.isEmpty()?"":a.get(rnd.nextInt(a.size()));}String safe(String s){return s==null?"":s;}
    String clean(String s){return s==null?"":s.replaceAll("\\s{2,}"," ").replace("・・","・").replace("のの","の").trim();}
    boolean repeatRun(String s){for(int i=0;i+2<s.length();i++)if(s.charAt(i)==s.charAt(i+1)&&s.charAt(i)==s.charAt(i+2))return true;return false;}
    String onlyKanji(String s){StringBuilder b=new StringBuilder();if(s!=null)for(int i=0;i<s.length();i++)if(isKanji(s.charAt(i)))b.append(s.charAt(i));return b.toString();}
    boolean isKanji(char c){return(c>='\u3400'&&c<='\u4DBF')||(c>='\u4E00'&&c<='\u9FFF')||c=='々';}
    byte[] read(InputStream in)throws Exception{ByteArrayOutputStream o=new ByteArrayOutputStream();byte[]b=new byte[8192];int n;while((n=in.read(b))>=0)o.write(b,0,n);in.close();return o.toByteArray();}
    String decode(byte[]b)throws Exception{int off=b.length>2&&(b[0]&255)==239&&(b[1]&255)==187&&(b[2]&255)==191?3:0;CharsetDecoder d=StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT);try{return d.decode(ByteBuffer.wrap(b,off,b.length-off)).toString();}catch(Exception e){return new String(b,Charset.forName("MS932"));}}
    String fileName(Uri u){try(Cursor c=getContentResolver().query(u,new String[]{OpenableColumns.DISPLAY_NAME},null,null,null)){if(c!=null&&c.moveToFirst()){int i=c.getColumnIndex(OpenableColumns.DISPLAY_NAME);if(i>=0)return c.getString(i);}}catch(Exception e){}return"imported.csv";}
    String cleanHead(String s){return s==null?"":s.replace("\uFEFF","").trim();}boolean isPattern(String s){return"pattern".equalsIgnoreCase(s)||"patterns".equalsIgnoreCase(s);}boolean nonempty(List<String>r){for(String s:r)if(s!=null&&!s.trim().isEmpty())return true;return false;}String join(List<String>a){StringBuilder b=new StringBuilder();for(String s:a){if(b.length()>0)b.append('\n');b.append(s);}return b.toString();}

    LinearLayout vbox(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;}LinearLayout hbox(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.HORIZONTAL);l.setGravity(Gravity.CENTER_VERTICAL);return l;}
    TextView txt(String s,int z,boolean bold){TextView v=new TextView(this);v.setText(s);v.setTextSize(z);v.setTextColor(Color.rgb(28,31,35));v.setTypeface(null,bold?Typeface.BOLD:Typeface.NORMAL);v.setPadding(0,dp(5),0,dp(5));return v;}TextView section(String s){TextView v=txt(s,14,true);v.setTextColor(Color.rgb(70,78,88));v.setPadding(0,dp(18),0,dp(6));return v;}
    EditText edit(String hint){EditText e=new EditText(this);e.setHint(hint);e.setSingleLine(true);e.setTextSize(15);return e;}CheckBox check(String s){CheckBox c=new CheckBox(this);c.setText(s);c.setTextSize(12);return c;}Button button(String s){Button b=new Button(this);b.setText(s);b.setAllCaps(false);return b;}
    Spinner spinner(List<String>a){Spinner s=new Spinner(this);s.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,a));return s;}View wrap(String label,View child){LinearLayout b=vbox();b.setPadding(dp(3),0,dp(3),0);b.addView(txt(label,11,false));b.addView(child,match(43));return b;}LinearLayout.LayoutParams weight(){return new LinearLayout.LayoutParams(0,-2,1);}LinearLayout.LayoutParams match(int h){return new LinearLayout.LayoutParams(-1,dp(h));}int dp(int v){return(int)(v*getResources().getDisplayMetrics().density+.5f);}
}
