package uwi.dcit.AgriExpenseTT;

import java.io.IOException;
import java.util.ArrayList;
import java.util.regex.Pattern;

import helper.CloudInterface;
import helper.DbHelper;
import helper.DbQuery;

import com.example.agriexpensett.upaccendpoint.model.UpAcc;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.Context;
import android.content.DialogInterface;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.util.Patterns;
import android.widget.Toast;

public class SignIn {
	Context context;
	SQLiteDatabase db;
	DbHelper dbh;
	public SignIn(Context context){
		this.context=context;
		dbh=new DbHelper(context);
		db=dbh.getReadableDatabase();
	}
	public SignIn(SQLiteDatabase db,DbHelper dbh,Context context){
		this.context=context;
		this.db=db;
		this.dbh=dbh;
	}
	public void signIn(){
		UpAcc acc=isExisting();
		if(acc==null){
			accSetUp();
		}else{
			try {
				Toast.makeText(context, acc.toPrettyString(),Toast.LENGTH_SHORT).show();
			} catch (IOException e) {	e.printStackTrace();}
		}
	}
	public void accSetUp(){
		System.out.println("sign in mc");
		ArrayList<String> accs=new ArrayList<String>();
		populateAcc(accs);
		if(accs.isEmpty()){
			Toast.makeText(context, "no accounts", Toast.LENGTH_SHORT).show();
			noAccs();
			return;
		}
			
		final CharSequence[] items= new CharSequence[accs.size()];
		int i=0;
		for(String k:accs)
			items[i]=accs.get(i++);
		
	    AlertDialog.Builder builder = new AlertDialog.Builder(context);
	    builder.setTitle("Select Account");
	    builder.setItems(items, new DialogInterface.OnClickListener() {
	        @Override
			public void onClick(DialogInterface dialog, int item) {
	           // Toast.makeText(context, items[item], Toast.LENGTH_SHORT).show();
	            String acc=convertString((items[item].toString()));
	        	//Toast.makeText(context,acc,Toast.LENGTH_LONG).show();
	            DbQuery.insertUpAcc(db, acc);
	            Toast.makeText(context, "signed in", Toast.LENGTH_SHORT).show();
	        }
	    }).show();
	}
	
	private void noAccs(){
		AlertDialog.Builder builder=new AlertDialog.Builder(context);
		builder.setTitle("No accounts available");
		builder.setMessage("There are no accounts available to sign in with,"
				+ " please go to your phone's settings and create and account");
		builder.setNeutralButton("ok", new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int item) {
				
			}
		});
		builder.show();
	}
	
	private void populateAcc(ArrayList<String> accs){
		Account[] accounts = AccountManager.get(context).getAccountsByType("com.google");
		for(Account a:accounts){
		  accs.add(a.name);
		  System.out.println(a.name);
		}
	}
	private String convertString(String str){
		int len=str.length();
		String newStr="_";
		for(int i=0;i<len;i++){
			if(isChar(str.charAt(i)))
					newStr+=str.charAt(i);
			else if(str.charAt(i)=='@')
				break;
		}
		return newStr;
	}
	private boolean isChar(char c){
		if((c>='a'&&c<='z')||(c>='A'&&c<='Z'))
			return true;
		return false;
	}
	private UpAcc isExisting(){
		String code="select * from "+DbHelper.TABLE_UPDATE_ACCOUNT;
		Cursor cursor=db.rawQuery(code, null);
		if(cursor.getCount()<1)
			return null;
		System.out.println("account exists !!!!!!!!");
		cursor.moveToFirst();
		UpAcc acc=new UpAcc();
		acc.setAcc(cursor.getString(cursor.getColumnIndex(DbHelper.UPDATE_ACCOUNT_ACC)));
		acc.setLastUpdated(cursor.getLong(cursor.getColumnIndex(DbHelper.UPDATE_ACCOUNT_UPDATED)));
		
		return acc;
	}
}	
