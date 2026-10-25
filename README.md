# Smart Mess (SMART TRANSPARENT MESS ERP)

নেটিভ Android অ্যাপ: Kotlin + Jetpack Compose। ব্যাকএন্ড: Supabase (Phase 1 থেকে)।
এই ধাপ (Phase 0) শুধু একটি ছোট অ্যাপ, যা APK হিসেবে বিল্ড হয়।

## ফোনে APK বানানো ও নামানো
1. রিপোজিটরিতে **Actions** ট্যাব খুলুন (না দেখলে **More** মেনু)।
2. বাঁ দিকে **Build APK** বেছে **Run workflow** চাপুন, তারপর সবুজ **Run workflow**।
3. কয়েক মিনিট পর ওই run-এ ঢুকুন। সবুজ টিক হলে একদম নিচে **Artifacts** থেকে `mess-erp-debug-apk` নামান।
4. ZIP খুলে ভেতরের `app-debug.apk` ইনস্টল করুন। "অজানা উৎস" অনুমতি লাগতে পারে।
5. লাল ক্রস এলে `build` ধাপের শেষ কয়েক লাইনের স্ক্রিনশট নিয়ে সাহায্য চান।

## অ্যাপের নাম বদল
`app/src/main/res/values/strings.xml` খুলে Edit চেপে `app_name`-এর মাঝের লেখা বদলান, তারপর Commit করুন।

## নতুন ফাইল ZIP দিয়ে যোগ করা
1. `https://github.com/<আপনার-ইউজার>/<রিপো>/upload/main` খুলুন।
2. ZIP-এর নাম `phase` দিয়ে শুরু হতে হবে (যেমন `phase1.zip`)। সরাসরি ZIP আপলোড করে Commit করুন।
3. **Unzip** workflow ফাইল খুলে বসাবে। এরপর হাতে **Build APK** চালান।

## Supabase কনফিগ (এখনো বাধ্যতামূলক নয়)
- Supabase-এ প্রজেক্ট বানিয়ে **Project Settings → API** থেকে `Project URL` ও `anon public` কী নিন।
- GitHub: **Settings → Secrets and variables → Actions → New repository secret**:
  - `SUPABASE_URL`
  - `SUPABASE_ANON_KEY`
- **কখনো** `service_role` কী, ডাটাবেস পাসওয়ার্ড বা সাইনিং কী কোডে, ZIP-এ বা এই ফাইলে রাখবেন না।
- anon কী APK-র ভেতরে থাকে এবং এটা স্বাভাবিক। আসল নিরাপত্তা Supabase-এর Row Level Security নিয়মে।

## বর্তমান অবস্থা
- Phase 0: ছোট অ্যাপ, বিল্ড workflow।
- Phase 1 (আসছে): লগইন, রোল (MEMBER/ADMIN/MAIN_ADMIN), প্রোফাইল, ড্যাশবোর্ড।
