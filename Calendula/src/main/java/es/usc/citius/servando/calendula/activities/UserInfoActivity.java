package es.usc.citius.servando.calendula.activities;

import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.view.ViewPager;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.afollestad.materialdialogs.DialogAction;
import com.afollestad.materialdialogs.MaterialDialog;
import com.github.javiersantos.materialstyleddialogs.MaterialStyledDialog;
import com.github.javiersantos.materialstyleddialogs.enums.Style;
import com.mikepenz.community_material_typeface_library.CommunityMaterial;

import java.sql.SQLException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;

import butterknife.BindView;
import butterknife.ButterKnife;
import es.usc.citius.servando.calendula.CalendulaActivity;
import es.usc.citius.servando.calendula.CalendulaApp;
import es.usc.citius.servando.calendula.R;
import es.usc.citius.servando.calendula.allergies.AllergenFacade;
import es.usc.citius.servando.calendula.allergies.AllergenVO;
import es.usc.citius.servando.calendula.allergies.AllergyAlertUtil;
import es.usc.citius.servando.calendula.database.DB;
import es.usc.citius.servando.calendula.drugdb.model.persistence.Prescription;
import es.usc.citius.servando.calendula.events.PersistenceEvents;
import es.usc.citius.servando.calendula.fragments.HomeFragment;
import es.usc.citius.servando.calendula.fragments.MedicineCreateOrEditFragment;
import es.usc.citius.servando.calendula.persistence.HealthData;
import es.usc.citius.servando.calendula.persistence.Medicine;
import es.usc.citius.servando.calendula.persistence.Patient;
import es.usc.citius.servando.calendula.persistence.PatientAlert;
import es.usc.citius.servando.calendula.persistence.alerts.AllergyPatientAlert;
import es.usc.citius.servando.calendula.util.FragmentUtils;
import es.usc.citius.servando.calendula.util.IconUtils;
import es.usc.citius.servando.calendula.util.LogUtil;
import es.usc.citius.servando.calendula.util.Snack;
import es.usc.citius.servando.calendula.util.alerts.AlertManager;

public class UserInfoActivity extends CalendulaActivity implements HomeFragment.OnHealthDataEditListener{


    public static final String EXTRA_SEARCH_TEXT = "MedicinesActivity.extras.SEARCH_TEXT";
    private static final String TAG = "MedicinesActivity";
    private static final int REQUEST_CODE_GET_MED = 1314;
    /**
     * The {@link android.support.v4.view.PagerAdapter} that will provide
     * fragments for each of the sections. We use a
     * {@link android.support.v4.app.FragmentPagerAdapter} derivative, which will keep every
     * loaded fragment in memory. If this becomes too memory intensive, it
     * may be best to switch to a
     * {@link android.support.v4.app.FragmentStatePagerAdapter}.
     */
    UserInfoActivity.SectionsPagerAdapter mSectionsPagerAdapter;
    /**
     * The {@link android.support.v4.view.ViewPager} that will host the section contents.
     */
    @BindView(R.id.pager)
    ViewPager mViewPager;
    String qrData;
    @BindView(R.id.add_button)
    FloatingActionButton fab;
    int color;

    private static final String STATE_STARTED_SEARCH = "STATE_STARTED_SEARCH";

    private Prescription prescriptionToSet = null;
    private String prescriptionNameToSet = null;
    private String intentAction;
    private String intentSearchText = null;
    private boolean startedSearch = false;



    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_remove:
                //noop
                break;
            default:
                finish();
                break;
        }
        return true;
    }


    Fragment getViewPagerFragment(int position) {
        return getSupportFragmentManager().findFragmentByTag(FragmentUtils.makeViewPagerFragmentName(R.id.pager, position));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_user_info);
        ButterKnife.bind(this);

        color = DB.patients().getActive(this).getColor();
        setupToolbar(null, color);
        setupStatusBar(color);

        processIntent();

        TextView title = ((TextView) findViewById(R.id.textView2));

        mSectionsPagerAdapter = new SectionsPagerAdapter(getSupportFragmentManager());
        mViewPager.setAdapter(mSectionsPagerAdapter);

        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ((HomeFragment) getViewPagerFragment(0)).onEdit();
            }
        });

        title.setBackgroundColor(color);


//        if (savedInstanceState != null && savedInstanceState.getBoolean(STATE_STARTED_SEARCH)) {
//            LogUtil.d(TAG, "onCreate: search was started, ignoring search intents");
//        } else if (mMedicineId == -1 || intentSearchText != null) {
//            showSearchView(intentSearchText);
//        }

    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putBoolean(STATE_STARTED_SEARCH, startedSearch);
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, final Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        LogUtil.d(TAG, "onActivityResult() called with: requestCode = [" + requestCode + "], resultCode = [" + resultCode + "], data = [" + data + "]");
        if (requestCode == REQUEST_CODE_GET_MED) {
            if (resultCode == RESULT_OK) {
                final String prescriptionName = data.getStringExtra(MedicinesSearchActivity.RETURN_EXTRA_PRESCRIPTION_NAME);
                mViewPager.post(new Runnable() {
                    @Override
                    public void run() {
                        if (prescriptionName != null) {
                            ((MedicineCreateOrEditFragment) getViewPagerFragment(0)).setMedicineName(prescriptionName);
                        } else {
                            final Prescription p = data.getParcelableExtra(MedicinesSearchActivity.RETURN_EXTRA_PRESCRIPTION);
                            if (p != null) {
                                ((MedicineCreateOrEditFragment) getViewPagerFragment(0)).setPrescription(p);
                            } else {
                                LogUtil.e(TAG, "onActivityResult: result was OK but no prescription extras received ");
                            }
                        }
                    }
                });
            }
        } else {
            LogUtil.w(TAG, "onActivityResult: invalid request code " + requestCode + ", ignoring");
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        overridePendingTransition(0, 0);
    }

    private void processIntent() {
//        mMedicineId = getIntent().getLongExtra(CalendulaApp.INTENT_EXTRA_MEDICINE_ID, -1);
        intentAction = getIntent().getStringExtra(CalendulaApp.INTENT_EXTRA_ACTION);
        intentSearchText = getIntent().getStringExtra(EXTRA_SEARCH_TEXT);
        qrData = getIntent().getStringExtra("qr_data");
    }


//    @Override
//    public void onUserCreated(Patient p) {
//        Patient patient = p;
//        Toast.makeText(this, "Health number has been saved", Toast.LENGTH_SHORT).show();
//        finish();
//    }

    @Override
    public void OnHealthDataEditListener(HealthData h) {
        Toast.makeText(this, "Health number has been saved", Toast.LENGTH_SHORT).show();
        finish();
    }

    /**
     * A {@link android.support.v4.app.FragmentPagerAdapter} that returns a fragment corresponding to
     * one of the sections/tabs/pages.
     */
    public class SectionsPagerAdapter extends FragmentPagerAdapter {

        public SectionsPagerAdapter(FragmentManager fm) {
            super(fm);
        }

        @Override
        public Fragment getItem(int position) {

            Fragment f = new HomeFragment();
            Bundle args = new Bundle();
//            args.putLong(CalendulaApp.INTENT_EXTRA_MEDICINE_ID, mMedicineId);
            args.putString(CalendulaApp.INTENT_EXTRA_ACTION, intentAction);
            f.setArguments(args);
            return f;
        }

        @Override
        public int getCount() {
            // Show 1 total pages.
            return 1;
        }

    }

}
