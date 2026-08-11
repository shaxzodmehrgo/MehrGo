package uz.teamwork.mehrgodriver.data.remote

//object RetrofitClient {
//    private val gsonConverterFactory = GsonConverterFactory.create()
//
//    private val headerInterceptor = object : Interceptor {
//        override fun intercept(chain: Interceptor.Chain): Response {
//            var request = chain.request()
//
//
//            request = request.newBuilder()
//                .addHeader("Authorization", UserManager.getBearerToken())
//                .addHeader("Accept-Language", LanguageManager.getLanguage() ?: "")
//                .build()
//
//            val response = chain.proceed(request)
//            return response
//        }
//    }
//
//    private val client = OkHttpClient.Builder()
////        .addInterceptor(ChuckerInterceptor.Builder(App.appContext).build())
//        .addInterceptor(headerInterceptor)
//        .connectTimeout(20, TimeUnit.SECONDS)
//        .readTimeout(30, TimeUnit.SECONDS)
//        .writeTimeout(40, TimeUnit.SECONDS)
//        .build()
//
//    // Main
//    private val retrofit = Retrofit.Builder()
//        .baseUrl(BASE_URL)
//        .client(client)
//        .addConverterFactory(gsonConverterFactory).build()
//
//    fun getApiService(): ApiService {
//        return retrofit.create(ApiService::class.java)
//    }
//
//    // Route
//    private val retrofitForRoute = Retrofit.Builder()
//        .baseUrl(BASE_URL_ROUTE)
//        .client(client)
//        .addConverterFactory(gsonConverterFactory).build()
//
//    fun getApiServiceForRoute(): RouteApiService {
//        return retrofitForRoute.create(RouteApiService::class.java)
//    }
//}