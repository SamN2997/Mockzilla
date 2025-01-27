import 'package:dio/dio.dart';
import 'package:example/engine/feature/packages/models.dart';
import 'package:example/engine/feature/packages/packages_client.dart';
import 'package:flutter/material.dart';
import 'package:mockzilla/mockzilla.dart';

import 'engine/config/mockzilla_config.dart';

void main() async {
  WidgetsFlutterBinding.ensureInitialized();
  final config = const MockzillaConfig(
    port: 8080,
    isRelease: false,
    localHostOnly: false,
    logLevel: LogLevel.debug,
    releaseModeConfig: ReleaseModeConfig(),
    additionalLogWriters: [],
  ).addEndpoint(
    () => EndpointConfig(
      name: "Fetch Packages",
      key: "fetch-packages",
      endpointMatcher: (request) =>
          RegExp(r"/packages").hasMatch(request.uri) &&
          request.method == HttpMethod.get,
      defaultHandler: (_) => defaultResponse,
      errorHandler: (_) => errorResponse,
    ),
  );
  await Mockzilla.startMockzilla(config);
  runApp(const MyApp());
}

class MyApp extends StatelessWidget {
  const MyApp({super.key});

  // This widget is the root of your application.
  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Flutter Demo',
      theme: ThemeData(
        colorScheme: ColorScheme.fromSeed(seedColor: Colors.deepPurple),
        useMaterial3: true,
      ),
      home: const PackagesList(),
    );
  }
}

class PackagesList extends StatefulWidget {
  const PackagesList({super.key});

  @override
  State<PackagesList> createState() => _PackagesListState();
}

class _PackagesListState extends State<PackagesList> {
  final _packagesClient = PackagesClient(Dio());
  late Future<FetchPackagesResponse> _future;

  @override
  initState() {
    super.initState();
    fetchPackages();
  }

  fetchPackages() {
    setState(() {
      _future = _packagesClient.fetchPackages();
    });
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text("Mockzilla Demo"),
        actions: [
          IconButton(
            onPressed: fetchPackages,
            icon: const Icon(Icons.refresh),
          )
        ],
      ),
      body: FutureBuilder(
        future: _future,
        builder: (context, snapshot) => switch (snapshot.connectionState) {
          ConnectionState.waiting => const Center(
              child: CircularProgressIndicator(),
            ),
          ConnectionState.done when snapshot.hasData => ListView.builder(
              itemBuilder: (context, index) {
                final package = snapshot.data!.packages[index];
                return PackageCard(package: package);
              },
              itemCount: snapshot.data!.packages.length,
            ),
          _ => Center(
              child: Text(
                  "Something went wrong! Error is: \n${snapshot.error.toString()}"),
            ),
        },
      ),
    );
  }
}

class PackageCard extends StatelessWidget {
  final Package package;

  const PackageCard({
    required this.package,
    super.key,
  });

  @override
  Widget build(BuildContext context) {
    return Card(
      child: Column(
        children: [
          Text(
            package.name,
            style: Theme.of(context).textTheme.headlineSmall,
          ),
          Text(package.description)
        ],
      ),
    );
  }
}
