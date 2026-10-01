package com.mayuto.dev.muziki;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipDescription;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.DialogInterface;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.DragEvent;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Random;

/**
 * Full playlist manager built only with Android framework APIs.
 */
public class PlaylistActivity extends AppCompatActivity {
    private LinearLayout root, header, actionRow, empty;
    private TextView title, stats, playlistName, playlistStats;
    private ImageView cover;
    private ListView list;
    private Button btnBack, btnAdd, btnPlay, btnShuffle, btnShare, btnMore;
    private PlaylistManager.Playlist playlist;
    private SongAdapter adapter;
    private ArrayList<String> allPaths = new ArrayList<String>();
    private ArrayList<String> allTitles = new ArrayList<String>();
    private HashMap<String,String> titleByPath = new HashMap<String,String>();
    private int draggedPosition = -1;
    private static final int PICK_COVER = 7001;

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        playlist = PlaylistManager.get(this, getIntent().getStringExtra("playlist_id"));
        if (playlist == null) { finish(); return; }
        buildUi();
        scanLibrary();
    }

    private TextView tv(String text, int size) {
        TextView v = new TextView(this);
        v.setText(text); v.setTextSize(size); v.setTextColor(Color.DKGRAY);
        return v;
    }

    private void buildUi() {
        root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(Color.WHITE);
        header = new LinearLayout(this); header.setGravity(Gravity.CENTER_VERTICAL); header.setPadding(16,18,12,14);
        header.setBackgroundColor(Color.rgb(255,64,129));
        btnBack = new Button(this); btnBack.setText("‹"); btnBack.setTextSize(28); btnBack.setTextColor(Color.WHITE); btnBack.setBackgroundColor(Color.TRANSPARENT);
        header.addView(btnBack,new LinearLayout.LayoutParams(52,60));
        title=tv(playlist.name,21); title.setTextColor(Color.WHITE); title.setTypeface(null,1);
        header.addView(title,new LinearLayout.LayoutParams(0,60,1));
        btnMore=new Button(this); btnMore.setText("⋮"); btnMore.setTextSize(24); btnMore.setTextColor(Color.WHITE); btnMore.setBackgroundColor(Color.TRANSPARENT);
        header.addView(btnMore,new LinearLayout.LayoutParams(52,60));
        root.addView(header);

        LinearLayout hero=new LinearLayout(this); hero.setPadding(18,18,18,12); hero.setGravity(Gravity.CENTER_VERTICAL);
        cover=new ImageView(this); cover.setImageResource(R.drawable.voice_note); cover.setScaleType(ImageView.ScaleType.CENTER_CROP);
        hero.addView(cover,new LinearLayout.LayoutParams(112,112));
        LinearLayout meta=new LinearLayout(this); meta.setOrientation(LinearLayout.VERTICAL); meta.setPadding(16,0,0,0);
        playlistName=tv(playlist.name,25); playlistName.setTypeface(null,1);
        playlistStats=tv("0 songs · 0m",14); playlistStats.setTextColor(Color.GRAY);
        meta.addView(playlistName); meta.addView(playlistStats);
        hero.addView(meta,new LinearLayout.LayoutParams(0,112,1));
        root.addView(hero);

        actionRow=new LinearLayout(this); actionRow.setGravity(Gravity.CENTER); actionRow.setPadding(8,4,8,10);
        btnPlay=new Button(this); btnPlay.setText("▶ Play all");
        btnShuffle=new Button(this); btnShuffle.setText("⤨ Shuffle");
        btnAdd=new Button(this); btnAdd.setText("+ Add");
        btnShare=new Button(this); btnShare.setText("Share");
        actionRow.addView(btnPlay,new LinearLayout.LayoutParams(0,52,1));
        actionRow.addView(btnShuffle,new LinearLayout.LayoutParams(0,52,1));
        actionRow.addView(btnAdd,new LinearLayout.LayoutParams(0,52,1));
        actionRow.addView(btnShare,new LinearLayout.LayoutParams(0,52,1));
        root.addView(actionRow);

        stats=tv("Drag and drop songs to reorder",12); stats.setGravity(Gravity.CENTER); stats.setTextColor(Color.GRAY); stats.setPadding(0,0,0,8);
        root.addView(stats,new LinearLayout.LayoutParams(-1,30));
        list=new ListView(this); list.setDivider(new ColorDrawable(0xFFEAEAEA)); list.setDividerHeight(1);
        root.addView(list,new LinearLayout.LayoutParams(-1,0,1));
        empty= new LinearLayout(this); empty.setGravity(Gravity.CENTER); empty.setOrientation(LinearLayout.VERTICAL);
        TextView e=tv("No songs in this playlist",17); empty.addView(e); empty.setVisibility(View.GONE); root.addView(empty,new LinearLayout.LayoutParams(-1,0,1));
        setContentView(root);

        btnBack.setOnClickListener(new View.OnClickListener(){public void onClick(View v){finish();}});
        btnMore.setOnClickListener(new View.OnClickListener(){public void onClick(View v){showPlaylistMenu();}});
        btnAdd.setOnClickListener(new View.OnClickListener(){public void onClick(View v){showAddSongs();}});
        btnPlay.setOnClickListener(new View.OnClickListener(){public void onClick(View v){play(false);}});
        btnShuffle.setOnClickListener(new View.OnClickListener(){public void onClick(View v){play(true);}});
        btnShare.setOnClickListener(new View.OnClickListener(){public void onClick(View v){share();}});
    }

    private void scanLibrary() {
        new Thread(new Runnable(){public void run(){
            final ArrayList<String> paths=new ArrayList<String>(), titles=new ArrayList<String>();
            Cursor c=null;
            try {
                c=getContentResolver().query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    new String[]{MediaStore.Audio.Media.DATA,MediaStore.Audio.Media.TITLE},
                    MediaStore.Audio.Media.IS_MUSIC+"!= 0",null,MediaStore.Audio.Media.TITLE+" ASC");
                if(c!=null) while(c.moveToNext()){
                    String p=c.getString(0), t=c.getString(1);
                    if(p!=null){paths.add(p);titles.add(t==null?new File(p).getName():t);}
                }
            } catch(Exception e){} finally{if(c!=null)c.close();}
            runOnUiThread(new Runnable(){public void run(){
                allPaths=paths; allTitles=titles; titleByPath.clear();
                for(int i=0;i<paths.size();i++) titleByPath.put(paths.get(i),titles.get(i));
                refresh();
            }});
        }}).start();
    }

    private void refresh() {
        title.setText(playlist.name); playlistName.setText(playlist.name);
        if(adapter==null){adapter=new SongAdapter(this);list.setAdapter(adapter);} else adapter.notifyDataSetChanged();
        updateStats();
        if(playlist.coverUri!=null && playlist.coverUri.length()>0){
            try{cover.setImageURI(Uri.parse(playlist.coverUri));}catch(Exception e){}
        }
        list.setVisibility(playlist.paths.size()==0?View.GONE:View.VISIBLE);
        empty.setVisibility(playlist.paths.size()==0?View.VISIBLE:View.GONE);
    }

    private void updateStats() {
        long total=0; int count=playlist.paths.size();
        for(String p:playlist.paths) total+=duration(p);
        playlistStats.setText(count+" "+(count==1?"song":"songs")+" · "+formatDuration(total));
        stats.setText(count+" songs · "+formatDuration(total)+"  •  Drag and drop to reorder");
    }

    private long duration(String path){
        MediaMetadataRetriever r=new MediaMetadataRetriever();
        try{r.setDataSource(path); String d=r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION); return d==null?0:Long.parseLong(d);}catch(Exception e){return 0;}finally{try{r.release();}catch(Exception e){}}
    }
    private String formatDuration(long ms){
        long sec=ms/1000, min=sec/60, h=min/60; sec%=60; min%=60;
        if(h>0)return h+"h "+min+"m"; return min+"m "+sec+"s";
    }

    private void showAddSongs(){
        final ArrayList<String> choices=new ArrayList<String>(); final ArrayList<Boolean> checked=new ArrayList<Boolean>();
        for(int i=0;i<allPaths.size();i++){choices.add(allTitles.get(i));checked.add(Boolean.valueOf(playlist.paths.contains(allPaths.get(i))));}
        AlertDialog.Builder b=new AlertDialog.Builder(this); b.setTitle("Add songs");
        String[] names=choices.toArray(new String[choices.size()]); boolean[] state=new boolean[checked.size()];
        for(int i=0;i<state.length;i++)state[i]=checked.get(i).booleanValue();
        b.setMultiChoiceItems(names,state,new DialogInterface.OnMultiChoiceClickListener(){public void onClick(DialogInterface d,int which,boolean isChecked){checked.set(which,Boolean.valueOf(isChecked));}});
        b.setPositiveButton("Save",new DialogInterface.OnClickListener(){public void onClick(DialogInterface d,int w){
            for(int i=0;i<choices.size();i++){String path=allPaths.get(i); if(checked.get(i).booleanValue())PlaylistManager.add(playlist,path); else PlaylistManager.remove(playlist,path);}
            PlaylistManager.save(PlaylistActivity.this,playlist); refresh();
        }}); b.setNegativeButton("Cancel",null); b.show();
    }

    private void showPlaylistMenu(){
        final String[] items={"Rename playlist","Change cover","Delete playlist"};
        new AlertDialog.Builder(this).setTitle(playlist.name).setItems(items,new DialogInterface.OnClickListener(){public void onClick(DialogInterface d,int which){
            if(which==0)rename(); else if(which==1)pickCover(); else confirmDelete();
        }}).show();
    }
    private void rename(){
        final EditText input=new EditText(this); input.setSingleLine(true); input.setText(playlist.name); input.setSelectAllOnFocus(true);
        new AlertDialog.Builder(this).setTitle("Rename playlist").setView(input).setPositiveButton("Save",new DialogInterface.OnClickListener(){public void onClick(DialogInterface d,int w){
            PlaylistManager.rename(PlaylistActivity.this,playlist,input.getText().toString()); refresh();
        }}).setNegativeButton("Cancel",null).show();
    }
    private void confirmDelete(){
        new AlertDialog.Builder(this).setTitle("Delete playlist?").setMessage("This removes the playlist, not the songs from your device.")
        .setPositiveButton("Delete",new DialogInterface.OnClickListener(){public void onClick(DialogInterface d,int w){PlaylistManager.delete(PlaylistActivity.this,playlist);finish();}})
        .setNegativeButton("Cancel",null).show();
    }
    private void pickCover(){startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("image/*").addCategory(Intent.CATEGORY_OPENABLE),PICK_COVER);}
    @Override protected void onActivityResult(int r,int c,Intent data){super.onActivityResult(r,c,data);if(r==PICK_COVER&&c==RESULT_OK&&data!=null&&data.getData()!=null){try{getContentResolver().takePersistableUriPermission(data.getData(),Intent.FLAG_GRANT_READ_URI_PERMISSION);}catch(Exception e){} PlaylistManager.setCover(this,playlist,data.getData().toString());refresh();}}

    private void play(boolean shuffle){
        ArrayList<String> paths=new ArrayList<String>(playlist.paths); if(paths.size()==0){Toast.makeText(this,"Playlist is empty",Toast.LENGTH_SHORT).show();return;}
        if(shuffle)Collections.shuffle(paths,new Random());
        ArrayList<String> titles=new ArrayList<String>(); for(String p:paths){String t=titleByPath.get(p);titles.add(t==null?new File(p).getName():t);}
        Intent i=new Intent(this,MusicService.class); i.setAction(MusicService.ACTION_PLAY_NEW); i.putStringArrayListExtra("paths",paths); i.putStringArrayListExtra("titles",titles); i.putExtra("index",0); startService(i);
        Toast.makeText(this,shuffle?"Shuffling "+playlist.name:"Playing "+playlist.name,Toast.LENGTH_SHORT).show();
    }
    private void share(){
        StringBuilder b=new StringBuilder(playlist.name+"\\n"+playlist.paths.size()+" songs · "+playlistStats.getText()+"\\n\\n");
        for(String p:playlist.paths)b.append("• ").append(titleByPath.containsKey(p)?titleByPath.get(p):new File(p).getName()).append("\\n");
        Intent i=new Intent(Intent.ACTION_SEND);i.setType("text/plain");i.putExtra(Intent.EXTRA_SUBJECT,"Muziki playlist: "+playlist.name);i.putExtra(Intent.EXTRA_TEXT,b.toString());startActivity(Intent.createChooser(i,"Share playlist"));
    }

    private class SongAdapter extends BaseAdapter {
        Context c; SongAdapter(Context c){this.c=c;}
        public int getCount(){return playlist.paths.size();}
        public Object getItem(int p){return playlist.paths.get(p);}
        public long getItemId(int p){return p;}
        public View getView(final int position,View convert,ViewGroup parent){
            LinearLayout row=new LinearLayout(c);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(14,8,8,8);
            TextView num=tv(String.valueOf(position+1),13);num.setGravity(Gravity.CENTER);row.addView(num,new LinearLayout.LayoutParams(36,58));
            TextView name=tv(titleByPath.containsKey(playlist.paths.get(position))?titleByPath.get(playlist.paths.get(position)):new File(playlist.paths.get(position)).getName(),16);name.setTypeface(null,1);
            row.addView(name,new LinearLayout.LayoutParams(0,58,1));
            TextView handle=tv("☰",22);handle.setGravity(Gravity.CENTER);handle.setContentDescription("Drag to reorder");
            row.addView(handle,new LinearLayout.LayoutParams(52,58));
            final int pos=position;
            View.OnLongClickListener dragStart=new View.OnLongClickListener(){public boolean onLongClick(View v){
                draggedPosition=pos; ClipData data=ClipData.newPlainText("playlist-position",String.valueOf(pos));
                if(Build.VERSION.SDK_INT>=24)v.startDragAndDrop(data,new View.DragShadowBuilder(v),v,0); else v.startDrag(data,new View.DragShadowBuilder(v),v,0); return true;
            }};
            handle.setOnLongClickListener(dragStart);
            row.setOnDragListener(new View.OnDragListener(){public boolean onDrag(View v,DragEvent e){
                if(e.getAction()==DragEvent.ACTION_DRAG_LOCATION && draggedPosition>=0){
                    int target=list.pointToPosition((int)e.getX(),(int)e.getY());
                    if(target>=0&&target<playlist.paths.size()&&target!=draggedPosition){
                        PlaylistManager.reorder(playlist,draggedPosition,target); draggedPosition=target; PlaylistManager.save(PlaylistActivity.this,playlist); adapter.notifyDataSetChanged(); list.setSelection(target);
                    }
                } else if(e.getAction()==DragEvent.ACTION_DRAG_ENDED){draggedPosition=-1;refresh();}
                return true;
            }});
            row.setOnClickListener(new View.OnClickListener(){public void onClick(View v){playFrom(pos);}});
            row.setOnLongClickListener(new View.OnLongClickListener(){public boolean onLongClick(View v){showRemove(position);return true;}});
            return row;
        }
    }
    private void showRemove(final int pos){
        new AlertDialog.Builder(this).setTitle("Remove song?").setMessage("Remove this song from "+playlist.name+"?")
        .setPositiveButton("Remove",new DialogInterface.OnClickListener(){public void onClick(DialogInterface d,int w){playlist.paths.remove(pos);PlaylistManager.save(PlaylistActivity.this,playlist);refresh();}})
        .setNegativeButton("Cancel",null).show();
    }
    private void playFrom(int pos){
        ArrayList<String> paths=new ArrayList<String>(playlist.paths), titles=new ArrayList<String>();
        for(String p:paths)titles.add(titleByPath.containsKey(p)?titleByPath.get(p):new File(p).getName());
        Intent i=new Intent(this,MusicService.class);i.setAction(MusicService.ACTION_PLAY_NEW);i.putStringArrayListExtra("paths",paths);i.putStringArrayListExtra("titles",titles);i.putExtra("index",pos);startService(i);
    }
}
